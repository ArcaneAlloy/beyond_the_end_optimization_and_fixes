package com.endersjourney.bteoptimizationfixes.compat.naturescompass;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Busqueda de bioma del Nature's Compass fuera del hilo del servidor.
 *
 * El original (BiomeSearchWorker) recorre una espiral de columnas desde el
 * jugador y, en cada una, pide el bioma a varias alturas al BiomeSource con el
 * Climate.Sampler. Lo hace en el hilo del servidor con el WorldWorkerManager
 * de Forge, que le da el tiempo libre de cada tick y como minimo 10 ms, asi
 * que mientras busca el servidor pierde al menos 10 ms por tick y la busqueda
 * va tan rapido como sobre tiempo.
 *
 * Aqui se recorre EXACTAMENTE la misma espiral (mismos pasos, mismos limites
 * de radio y de muestras, mismas alturas) en un hilo propio, y las columnas se
 * comprueban por lotes en paralelo. De cada lote se queda la PRIMERA columna
 * (en el orden de la espiral) que tiene el bioma, asi que el resultado es el
 * mismo que daria el original: misma posicion y mismo numero de muestras.
 *
 * El BiomeSource y el Climate.Sampler son los mismos que usa la generacion de
 * chunks desde varios hilos a la vez, asi que consultarlos en paralelo es
 * seguro.
 */
public final class AsyncBiomeSearch {

    /** Columnas por lote. Un lote se comprueba en paralelo. */
    private static final int BATCH = 256;

    private static final AtomicInteger THREADS = new AtomicInteger();
    private static final ExecutorService COORDINATOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "BTEOpt-BiomeSearch-" + THREADS.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    /** Hilos que comprueban columnas: deja libres al menos 2 nucleos (servidor y render). */
    private static final ForkJoinPool POOL = new ForkJoinPool(
            Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors() - 2)),
            pool -> {
                ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                t.setName("BTEOpt-BiomeSearch-Worker-" + t.getPoolIndex());
                t.setDaemon(true);
                return t;
            }, null, false);

    private AsyncBiomeSearch() {
    }

    /** Estado de una busqueda. Los campos de resultado solo son validos con isDone() == true. */
    public static final class Job {
        private volatile boolean done;
        private volatile boolean cancelled;
        private volatile int radius;
        private boolean found;
        private int x;
        private int z;
        private int samples;

        public boolean isDone() { return done; }
        public void cancel() { cancelled = true; }
        public int radius() { return radius; }
        public boolean found() { return found; }
        public int x() { return x; }
        public int z() { return z; }
        public int samples() { return samples; }
    }

    // Direcciones como en BiomeSearchWorker: UP solo al principio, luego N -> E -> S -> O (sentido horario)
    private static final int UP = 0, NORTH = 1, EAST = 2, SOUTH = 3, WEST = 4;

    public static Job start(ServerLevel level, ResourceLocation biomeKey, BlockPos startPos,
                            int sampleSpace, int maxSamples, int maxRadius, int[] yValues) {
        Job job = new Job();
        // Todo lo del nivel se toma aqui, en el hilo del servidor
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        Registry<Biome> registry = level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        int[] quartY = new int[yValues.length];
        for (int i = 0; i < yValues.length; i++) quartY[i] = QuartPos.fromBlock(yValues[i]);

        COORDINATOR.execute(() -> {
            try {
                run(job, source, sampler, registry, biomeKey, startPos, sampleSpace, maxSamples, maxRadius, quartY);
            } catch (Throwable t) {
                BTEOptimizationFixes.LOGGER.error("[bteopt] Error en la busqueda de bioma del Nature's Compass", t);
                job.found = false;
                job.x = startPos.getX();
                job.z = startPos.getZ();
                job.samples = 0;
            } finally {
                job.done = true;
            }
        });
        return job;
    }

    private static void run(Job job, BiomeSource source, Climate.Sampler sampler, Registry<Biome> registry,
                            ResourceLocation biomeKey, BlockPos startPos, int sampleSpace, int maxSamples,
                            int maxRadius, int[] quartY) throws Exception {
        int x = startPos.getX();
        int z = startPos.getZ();
        int direction = UP;
        int length = 0;
        int nextLength = sampleSpace;
        int samples = 0;

        int[] bx = new int[BATCH];
        int[] bz = new int[BATCH];
        int[] bs = new int[BATCH];

        while (!job.cancelled) {
            // 1) Siguientes columnas de la espiral, con los mismos limites que hasWork()
            int n = 0;
            boolean exhausted = false;
            while (n < BATCH) {
                if (!(distance(startPos, x, z) <= maxRadius && samples <= maxSamples)) {
                    exhausted = true;
                    break;
                }
                switch (direction) {
                    case NORTH -> z -= sampleSpace;
                    case EAST -> x += sampleSpace;
                    case SOUTH -> z += sampleSpace;
                    case WEST -> x -= sampleSpace;
                    default -> { }
                }
                bx[n] = x;
                bz[n] = z;
                bs[n] = samples; // succeed() se llama antes de samples++
                n++;

                samples++;
                length += sampleSpace;
                if (length >= nextLength) {
                    if (direction != UP) {
                        nextLength += sampleSpace;
                        direction = direction == WEST ? NORTH : direction + 1;
                    } else {
                        direction = NORTH;
                    }
                    length = 0;
                }
            }

            // 2) Comprobar el lote en paralelo y quedarse con la primera columna que tiene el bioma
            if (n > 0) {
                final int count = n;
                int first = POOL.submit(() -> IntStream.range(0, count).parallel()
                        .filter(i -> !job.cancelled && hasBiome(source, sampler, registry, biomeKey, bx[i], bz[i], quartY))
                        .min().orElse(-1)).get();
                if (job.cancelled) return;
                if (first >= 0) {
                    job.found = true;
                    job.x = bx[first];
                    job.z = bz[first];
                    job.samples = bs[first];
                    return;
                }
            }
            job.radius = distance(startPos, x, z);

            // 3) Sin mas columnas: falla con el estado final, como fail()
            if (exhausted) {
                job.found = false;
                job.x = x;
                job.z = z;
                job.samples = samples;
                return;
            }
        }
    }

    private static boolean hasBiome(BiomeSource source, Climate.Sampler sampler, Registry<Biome> registry,
                                    ResourceLocation biomeKey, int x, int z, int[] quartY) {
        int qx = QuartPos.fromBlock(x);
        int qz = QuartPos.fromBlock(z);
        for (int qy : quartY) {
            Biome biome = source.getNoiseBiome(qx, qy, qz, sampler).value();
            if (biomeKey.equals(registry.getKey(biome))) return true;
        }
        return false;
    }

    /**
     * ¿Puede el generador de esta dimension producir ese bioma? Es la lista que usa
     * el propio juego (BiomeSource#possibleBiomes): un bioma que no esta en ella no
     * aparece en ninguna columna, asi que buscarlo siempre acaba fallando.
     */
    public static boolean canGenerate(ServerLevel level, ResourceLocation biomeKey) {
        for (Holder<Biome> holder : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
            if (holder.is(biomeKey)) return true;
        }
        return false;
    }

    /** Ids de los biomas que el generador de esta dimension puede producir. */
    public static java.util.Set<ResourceLocation> possibleBiomeKeys(ServerLevel level) {
        java.util.Set<ResourceLocation> keys = new java.util.HashSet<>();
        for (Holder<Biome> holder : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
            holder.unwrapKey().ifPresent(k -> keys.add(k.location()));
        }
        return keys;
    }

    /** Igual que BiomeUtils.getDistanceToBiome(BlockPos, int, int). */
    public static int distance(BlockPos start, int x, int z) {
        return (int) Mth.sqrt((float) start.distSqr(new BlockPos(x, start.getY(), z)));
    }
}
