package com.endersjourney.bteoptimizationfixes.worldgen;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Optimizaciones de generación de mundo que no cambian el resultado (mismo mundo bloque a bloque).
 *
 * 1. Estructuras imposibles en la dimensión (mixin worldgen.StructureImpossibleSkipMixin).
 *    En 1.19.2, la fase structure_starts recorre TODOS los conjuntos de estructuras del juego en cada dimensión. Para
 *    cada una que toca en el chunk, Structure.generate busca su punto de generación (a menudo calculando columnas de
 *    ruido enteras, 3-4 ms cada una con Tectonic) y solo al final comprueba el bioma. Una estructura cuyos biomas no
 *    puede dar nunca el generador de biomas de esa dimensión siempre falla esa comprobación: los fósiles del Nether en
 *    el Overworld, las estructuras del Overworld en el End... Para esas, tryGenerateStructure devuelve "no se genera"
 *    sin hacer el trabajo. Es la misma comprobación que hace el propio juego (ChunkGenerator.generatePositions) para
 *    decidir qué estructuras calcula de antemano. El orden y el sorteo entre estructuras de un mismo conjunto no
 *    cambian: solo se ahorra el intento que iba a fallar.
 *
 * 2. Caché de biomas al aplicar las reglas de superficie (SurfaceBiomeCache).
 *    Las condiciones de bioma de las reglas de superficie piden el bioma de casi cada bloque de piedra del chunk
 *    (~32.000 por chunk en el Overworld). Cada consulta hace el "zoom" de biomas de vanilla: 8 celdas vecinas, cada una
 *    con 9 pasos de hash, y una lectura del bioma de la celda elegida. Las celdas se repiten muchísimo entre bloques
 *    vecinos, así que durante cada chunk se guardan sus valores de hash y su bioma y se repite exactamente la misma
 *    cuenta en coma flotante. El resultado es el mismo bioma que daría vanilla.
 *
 * 3. Reutilizar el NoiseChunk de chunks descargados y recargados a medio generar (NoiseChunkCache).
 *    Vanilla lo crea en la fase biomes y lo reutiliza en noise, surface y carvers, pero no lo guarda en disco: si el
 *    chunk se descarga entre fases, la siguiente lo vuelve a crear (en el End, ~12 ms cada vez). Se le devuelve el mismo
 *    objeto que habría tenido sin la descarga.
 *
 * 4. Caché de la altura de las islas del End ("minecraft:end_islands", EndIslandCacheMixin): solo depende de
 *    (x / 8, z / 8) y cuesta 625 muestras de ruido; Nullscape la usa sin caché.
 * 5. Caché del bioma "sin chunk" (ServerLevel.getUncachedNoiseBiome, UncachedBiomeCacheMixin): Quark y Dynamic Trees
 *    piden el bioma de posiciones de chunks no cargados, el mismo desde muchos chunks.
 * 6. Alturas del chunk guardadas en el chunk (ChunkHeightCacheMixin): getMinBuildHeight/getHeight se piden varias
 *    veces por cada bloque leído.
 *
 * Comprobación del punto 3: /bteopt genopt noisehash start|stop guarda una huella de los bloques de cada chunk justo
 * después del relleno del terreno (logs/bteopt_noisehash_<fecha>.txt). Dos pasadas con la misma semilla, una con las
 * optimizaciones y otra sin ellas, deben dar las mismas huellas.
 *
 * Comando (operador): /bteopt genopt               estado y contadores
 *                     /bteopt genopt on|off        activar o desactivar las dos optimizaciones (activadas por defecto)
 *                     /bteopt genopt verify on|off modo de verificación: calcula también lo que haría vanilla y cuenta
 *                                                  cualquier diferencia (más lento; solo para comprobar)
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldgenOpt {

    public static volatile boolean enabled = true;
    public static volatile boolean verify = false;

    public static final LongAdder STRUCTURES_SKIPPED = new LongAdder();
    public static final LongAdder STRUCTURES_VERIFIED = new LongAdder();
    public static final LongAdder STRUCTURES_MISMATCH = new LongAdder();
    public static final LongAdder BIOMES_CACHED = new LongAdder();
    public static final LongAdder BIOMES_VERIFIED = new LongAdder();
    public static final LongAdder BIOMES_MISMATCH = new LongAdder();
    public static final LongAdder NOISECHUNKS_REUSED = new LongAdder();
    public static final LongAdder NOISECHUNKS_CREATED = new LongAdder();
    public static final LongAdder ISLANDS_VERIFIED = new LongAdder();
    public static final LongAdder ISLANDS_MISMATCH = new LongAdder();
    public static final LongAdder UNCACHED_BIOMES_HIT = new LongAdder();
    public static final LongAdder UNCACHED_BIOMES_VERIFIED = new LongAdder();
    public static final LongAdder UNCACHED_BIOMES_MISMATCH = new LongAdder();
    private static final AtomicInteger MISMATCH_LOGS = new AtomicInteger();

    /** Para el modo verificación: estructura saltable que se está dejando a vanilla, y bioma que dio la caché. */
    public static final ThreadLocal<Structure> VERIFYING_STRUCTURE = new ThreadLocal<>();
    public static final ThreadLocal<Holder<Biome>> EXPECTED_BIOME = new ThreadLocal<>();

    /** Por generador de biomas: estructura -> puede aparecer algún bioma suyo en esta dimensión. */
    private static final Map<BiomeSource, Map<Structure, Boolean>> POSSIBLE = Collections.synchronizedMap(new WeakHashMap<>());

    private WorldgenOpt() {
    }

    public static boolean canEverSpawn(BiomeSource biomeSource, Structure structure) {
        Map<Structure, Boolean> perSource = POSSIBLE.computeIfAbsent(biomeSource, k -> new ConcurrentHashMap<>());
        return perSource.computeIfAbsent(structure, s -> {
            Set<Holder<Biome>> possible = biomeSource.possibleBiomes();
            return s.biomes().stream().anyMatch(possible::contains);
        });
    }

    public static void mismatch(String what) {
        if (MISMATCH_LOGS.incrementAndGet() <= 20) {
            BTEOptimizationFixes.LOGGER.warn("[bteopt-genopt] DIFERENCIA con vanilla: {}", what);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("bteopt")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("genopt")
                        .executes(WorldgenOpt::show)
                        .then(Commands.literal("on").executes(ctx -> set(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> set(ctx, false)))
                        .then(Commands.literal("noisehash")
                                .then(Commands.literal("start").executes(WorldgenOpt::hashStart))
                                .then(Commands.literal("stop").executes(WorldgenOpt::hashStop)))
                        .then(Commands.literal("verify")
                                .then(Commands.literal("on").executes(ctx -> setVerify(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setVerify(ctx, false))))));
    }

    /** Al cerrar el mundo se vacía la caché de NoiseChunk, para no retener nada del mundo anterior. */
    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        NoiseChunkCache.clear();
        hashing = false;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, boolean value) {
        enabled = value;
        return show(ctx);
    }

    private static int setVerify(CommandContext<CommandSourceStack> ctx, boolean value) {
        verify = value;
        STRUCTURES_SKIPPED.reset();
        STRUCTURES_VERIFIED.reset();
        STRUCTURES_MISMATCH.reset();
        BIOMES_CACHED.reset();
        BIOMES_VERIFIED.reset();
        BIOMES_MISMATCH.reset();
        NOISECHUNKS_REUSED.reset();
        NOISECHUNKS_CREATED.reset();
        ISLANDS_VERIFIED.reset();
        ISLANDS_MISMATCH.reset();
        UNCACHED_BIOMES_HIT.reset();
        UNCACHED_BIOMES_VERIFIED.reset();
        UNCACHED_BIOMES_MISMATCH.reset();
        MISMATCH_LOGS.set(0);
        return show(ctx);
    }

    // ---- Huellas del relleno del terreno (comprobación) ----

    public static volatile boolean hashing = false;
    private static final ConcurrentHashMap<Long, Long> HASHES = new ConcurrentHashMap<>();

    /** Huella de todos los bloques del chunk, en orden fijo. Se llama al terminar NoiseBasedChunkGenerator.doFill. */
    public static void recordNoiseHash(net.minecraft.world.level.chunk.ChunkAccess chunk) {
        if (!hashing || chunk == null) return;
        long h = 0xcbf29ce484222325L;
        net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            net.minecraft.world.level.chunk.LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) {
                h = (h ^ (-1 - i)) * 0x100000001b3L;
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int id = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(section.getBlockState(x, y, z));
                        h = (h ^ id) * 0x100000001b3L;
                    }
                }
            }
        }
        HASHES.put(chunk.getPos().toLong(), h);
    }

    private static int hashStart(CommandContext<CommandSourceStack> ctx) {
        HASHES.clear();
        hashing = true;
        ctx.getSource().sendSuccess(Component.literal("[bteopt-genopt] Grabando huellas del relleno del terreno"), false);
        return 1;
    }

    private static int hashStop(CommandContext<CommandSourceStack> ctx) {
        hashing = false;
        try {
            java.nio.file.Path dir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("logs");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve("bteopt_noisehash_" + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")) + ".txt");
            java.util.List<Long> keys = new java.util.ArrayList<>(HASHES.keySet());
            java.util.Collections.sort(keys);
            try (java.io.Writer w = java.nio.file.Files.newBufferedWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                for (long k : keys) {
                    net.minecraft.world.level.ChunkPos p = new net.minecraft.world.level.ChunkPos(k);
                    w.write(p.x + " " + p.z + " " + Long.toHexString(HASHES.get(k)) + "\n");
                }
            }
            String line = "[bteopt-genopt] " + keys.size() + " huellas guardadas en " + file.getFileName();
            ctx.getSource().sendSuccess(Component.literal(line), false);
            BTEOptimizationFixes.LOGGER.info(line);
        } catch (java.io.IOException e) {
            ctx.getSource().sendFailure(Component.literal("[bteopt-genopt] No se pudieron guardar las huellas: " + e));
        }
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        String[] lines = {
                "[bteopt-genopt] Optimizaciones: " + (enabled ? "activadas" : "desactivadas") + ", verificación: " + (verify ? "activada" : "desactivada"),
                "[bteopt-genopt] Estructuras imposibles saltadas: " + STRUCTURES_SKIPPED.sum()
                        + "; verificadas: " + STRUCTURES_VERIFIED.sum() + ", diferencias: " + STRUCTURES_MISMATCH.sum(),
                "[bteopt-genopt] Biomas servidos desde la caché: " + BIOMES_CACHED.sum()
                        + "; verificados: " + BIOMES_VERIFIED.sum() + ", diferencias: " + BIOMES_MISMATCH.sum(),
                "[bteopt-genopt] NoiseChunk creados: " + NOISECHUNKS_CREATED.sum() + ", reutilizados tras recargar el chunk: " + NOISECHUNKS_REUSED.sum()
                        + (hashing ? "; huellas del relleno: " + HASHES.size() + " chunks (grabando)" : ""),
                "[bteopt-genopt] Islas del End verificadas: " + ISLANDS_VERIFIED.sum() + ", diferencias: " + ISLANDS_MISMATCH.sum(),
                "[bteopt-genopt] Biomas sin chunk servidos desde la caché: " + UNCACHED_BIOMES_HIT.sum()
                        + "; verificados: " + UNCACHED_BIOMES_VERIFIED.sum() + ", diferencias: " + UNCACHED_BIOMES_MISMATCH.sum()
        };
        for (String line : lines) {
            ctx.getSource().sendSuccess(Component.literal(line), false);
            BTEOptimizationFixes.LOGGER.info(line);
        }
        return 1;
    }
}
