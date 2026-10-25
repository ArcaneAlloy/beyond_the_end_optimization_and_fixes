package com.endersjourney.bteoptimizationfixes.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.function.Function;

/**
 * Caché de BiomeManager.getBiome mientras se aplican las reglas de superficie de un chunk (ver WorldgenOpt).
 *
 * Vanilla (BiomeManager.getBiome): para el bloque (x, y, z) mira las 8 celdas de 4x4x4 que lo rodean, calcula para cada
 * una un desplazamiento pseudoaleatorio a partir de la semilla y de la celda (getFiddledDistance), se queda con la más
 * cercana y devuelve el bioma de esa celda. Aquí se guardan, por celda, esos tres desplazamientos y el bioma, y la
 * distancia se calcula con exactamente las mismas operaciones y en el mismo orden, así que el resultado es idéntico.
 *
 * Solo está activa entre el principio y el final de SurfaceSystem.buildSurface, en el hilo que la ejecuta y para el
 * BiomeManager que recibe esa llamada: se le da a las reglas de superficie una función de biomas propia (wrapCurrent) en vez
 * de la de vanilla. Cualquier consulta fuera del rango del chunk se deja a vanilla.
 */
public final class SurfaceBiomeCache {

    private static final ThreadLocal<SurfaceBiomeCache> CURRENT = ThreadLocal.withInitial(SurfaceBiomeCache::new);

    private BiomeManager manager;
    private BiomeManager.NoiseBiomeSource source;
    private long seed;
    private int x0, y0, z0, nx, ny, nz;
    private int gen;
    private long served;
    private int[] fiddleStamp = new int[0];
    private int[] biomeStamp = new int[0];
    private double[] f0 = new double[0], f1 = new double[0], f2 = new double[0];
    private Object[] biomes = new Object[0];
    /** Por celda base (la de menor x, y, z de las 8 candidatas): el bioma si las 8 tienen el mismo, o MIXED. */
    private Object[] uniform = new Object[0];
    private int[] uniformStamp = new int[0];
    private static final Object MIXED = new Object();

    private SurfaceBiomeCache() {
    }

    /** Al empezar SurfaceSystem.buildSurface: prepara la caché del hilo para este chunk y este BiomeManager. */
    public static void begin(BiomeManager manager, ChunkAccess chunk) {
        SurfaceBiomeCache c = CURRENT.get();
        c.manager = manager;
        BiomeManagerAccess acc = (BiomeManagerAccess) manager;
        c.source = acc.bteopt$getNoiseBiomeSource();
        c.seed = acc.bteopt$getBiomeZoomSeed();
        ChunkPos pos = chunk.getPos();
        int minX = pos.getMinBlockX(), minZ = pos.getMinBlockZ();
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight();
        // Celdas que puede usar un bloque del chunk: de ((min - 2) >> 2) a ((max - 2) >> 2) + 1
        c.x0 = (minX - 2) >> 2;
        c.nx = ((minX + 15 - 2) >> 2) + 2 - c.x0;
        c.z0 = (minZ - 2) >> 2;
        c.nz = ((minZ + 15 - 2) >> 2) + 2 - c.z0;
        c.y0 = (minY - 2) >> 2;
        c.ny = ((maxY - 2) >> 2) + 2 - c.y0;
        int size = c.nx * c.ny * c.nz;
        if (c.fiddleStamp.length < size) {
            c.fiddleStamp = new int[size];
            c.biomeStamp = new int[size];
            c.f0 = new double[size];
            c.f1 = new double[size];
            c.f2 = new double[size];
            c.biomes = new Object[size];
            c.uniform = new Object[size];
            c.uniformStamp = new int[size];
            c.gen = 0;
        }
        if (++c.gen == 0) { // vuelta completa del contador: limpiar marcas
            java.util.Arrays.fill(c.fiddleStamp, 0);
            java.util.Arrays.fill(c.biomeStamp, 0);
            java.util.Arrays.fill(c.uniformStamp, 0);
            c.gen = 1;
        }
    }

    /** Al terminar SurfaceSystem.buildSurface. */
    public static void end() {
        SurfaceBiomeCache c = CURRENT.get();
        if (c.manager != null) {
            c.manager = null;
            c.source = null;
            if (c.served > 0) WorldgenOpt.BIOMES_CACHED.add(c.served);
            c.served = 0;
        }
    }

    /**
     * La función de biomas que reciben las reglas de superficie (SurfaceRules.Context): responde desde la caché y, si
     * la posición se sale del chunk o no hay caché activa, llama a la de vanilla. En modo verificación calcula las dos
     * y compara. Sin ThreadLocal ni objetos de Mixin por consulta: solo la cuenta.
     */
    public static Function<BlockPos, Holder<Biome>> wrapCurrent(Function<BlockPos, Holder<Biome>> vanilla) {
        SurfaceBiomeCache c = CURRENT.get();
        BiomeManager manager = c.manager;
        if (manager == null) return vanilla;
        return pos -> {
            Holder<Biome> fast = c.manager == manager ? c.getBiome(pos) : null;
            if (fast == null) return vanilla.apply(pos);
            if (WorldgenOpt.verify) {
                Holder<Biome> slow = vanilla.apply(pos);
                WorldgenOpt.BIOMES_VERIFIED.increment();
                if (fast != slow && !java.util.Objects.equals(fast, slow)) {
                    WorldgenOpt.BIOMES_MISMATCH.increment();
                    WorldgenOpt.mismatch("bioma distinto en " + pos + ": caché " + fast.unwrapKey().map(Object::toString).orElse("?")
                            + ", vanilla " + slow.unwrapKey().map(Object::toString).orElse("?"));
                }
                return slow;
            }
            c.served++;
            return fast;
        };
    }

    /** Mismo resultado que BiomeManager.getBiome(pos), o null si la posición se sale del chunk (que lo haga vanilla). */
    @SuppressWarnings("unchecked")
    public Holder<Biome> getBiome(BlockPos pos) {
        BiomeManager.NoiseBiomeSource noiseBiomeSource = this.source;
        int i = pos.getX() - 2;
        int j = pos.getY() - 2;
        int k = pos.getZ() - 2;
        int l = i >> 2;
        int i1 = j >> 2;
        int j1 = k >> 2;
        int bx = l - x0, by = i1 - y0, bz = j1 - z0;
        if (bx < 0 || by < 0 || bz < 0 || bx + 1 >= nx || by + 1 >= ny || bz + 1 >= nz) return null;
        // Atajo exacto: si las 8 celdas candidatas tienen el mismo bioma, gane la que gane el resultado es ese bioma
        int base = index(bx, by, bz);
        if (uniformStamp[base] != gen) computeUniform(base, bx, by, bz, l, i1, j1, noiseBiomeSource);
        Object u = uniform[base];
        if (u != MIXED) return (Holder<Biome>) u;
        double d0 = (double) (i & 3) / 4.0;
        double d1 = (double) (j & 3) / 4.0;
        double d2 = (double) (k & 3) / 4.0;
        int best = 0;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int c = 0; c < 8; ++c) {
            boolean lowX = (c & 4) == 0;
            boolean lowY = (c & 2) == 0;
            boolean lowZ = (c & 1) == 0;
            int idx = index(lowX ? bx : bx + 1, lowY ? by : by + 1, lowZ ? bz : bz + 1);
            if (fiddleStamp[idx] != gen) {
                computeFiddles(idx, lowX ? l : l + 1, lowY ? i1 : i1 + 1, lowZ ? j1 : j1 + 1);
            }
            double ox = lowX ? d0 : d0 - 1.0;
            double oy = lowY ? d1 : d1 - 1.0;
            double oz = lowZ ? d2 : d2 - 1.0;
            // Mismo orden que BiomeManager.getFiddledDistance: z, luego y, luego x
            double dist = Mth.square(oz + f2[idx]) + Mth.square(oy + f1[idx]) + Mth.square(ox + f0[idx]);
            if (!(bestDist > dist)) continue;
            best = c;
            bestDist = dist;
        }
        int cx = (best & 4) == 0 ? l : l + 1;
        int cy = (best & 2) == 0 ? i1 : i1 + 1;
        int cz = (best & 1) == 0 ? j1 : j1 + 1;
        int idx = index(cx - x0, cy - y0, cz - z0);
        if (biomeStamp[idx] != gen) {
            biomes[idx] = noiseBiomeSource.getNoiseBiome(cx, cy, cz);
            biomeStamp[idx] = gen;
        }
        return (Holder<Biome>) biomes[idx];
    }

    private Object cellBiome(int bx, int by, int bz, int cx, int cy, int cz, BiomeManager.NoiseBiomeSource noiseBiomeSource) {
        int idx = index(bx, by, bz);
        if (biomeStamp[idx] != gen) {
            biomes[idx] = noiseBiomeSource.getNoiseBiome(cx, cy, cz);
            biomeStamp[idx] = gen;
        }
        return biomes[idx];
    }

    private void computeUniform(int base, int bx, int by, int bz, int l, int i1, int j1, BiomeManager.NoiseBiomeSource src) {
        Object first = cellBiome(bx, by, bz, l, i1, j1, src);
        Object result = first;
        for (int c = 1; c < 8 && result != MIXED; ++c) {
            int ox = (c & 4) == 0 ? 0 : 1;
            int oy = (c & 2) == 0 ? 0 : 1;
            int oz = (c & 1) == 0 ? 0 : 1;
            if (cellBiome(bx + ox, by + oy, bz + oz, l + ox, i1 + oy, j1 + oz, src) != first) result = MIXED;
        }
        uniform[base] = result;
        uniformStamp[base] = gen;
    }

    private int index(int bx, int by, int bz) {
        return (bx * ny + by) * nz + bz;
    }

    /** Lo mismo que hace BiomeManager.getFiddledDistance con la semilla y la celda, guardando los tres desplazamientos. */
    private void computeFiddles(int idx, int cellX, int cellY, int cellZ) {
        long l = seed;
        l = LinearCongruentialGenerator.next(l, cellX);
        l = LinearCongruentialGenerator.next(l, cellY);
        l = LinearCongruentialGenerator.next(l, cellZ);
        l = LinearCongruentialGenerator.next(l, cellX);
        l = LinearCongruentialGenerator.next(l, cellY);
        l = LinearCongruentialGenerator.next(l, cellZ);
        f0[idx] = fiddle(l);
        l = LinearCongruentialGenerator.next(l, seed);
        f1[idx] = fiddle(l);
        l = LinearCongruentialGenerator.next(l, seed);
        f2[idx] = fiddle(l);
        fiddleStamp[idx] = gen;
    }

    private static double fiddle(long value) {
        double d = (double) Math.floorMod(value >> 24, 1024) / 1024.0;
        return (d - 0.5) * 0.9;
    }
}
