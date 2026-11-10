package com.endersjourney.bteoptimizationfixes.compat.protectyourstructures;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Las estructuras protegidas por Protect Your Structures (config/protectyourstructures.json) no pueden tener otra
 * estructura pegada encima. Ejemplo real: red:jungle_deforestation generada dentro de
 * dungeons_enhanced:jungle_monument (semilla 670513265704819556, -4274 70 -3640).
 *
 * Cuando una estructura NO protegida va a empezar en un chunk, se mira si alguna protegida empieza cerca. Para cada set
 * protegido (colocacion random_spread) se calculan sus posibles chunks de inicio a partir de la semilla, como hace el
 * propio juego, y se genera la estructura protegida en ese chunk para saber si de verdad sale (bioma, terreno...) y que
 * caja ocupa. Si las cajas se tocan (con MARGIN bloques de separacion), la no protegida no se genera ahi.
 *
 * - No depende del orden en que se generen los chunks: el resultado es siempre el mismo para la misma semilla.
 * - La distancia depende del tamano real: se comparan las cajas de las dos estructuras, no un radio fijo.
 * - Las protegidas no cambian: ni su frecuencia ni su posicion. Entre dos protegidas no se aplica nada.
 * - En los sets con varias estructuras (Dungeons Arise agrupa protegidas y no protegidas en el mismo set) se repite el
 *   sorteo del juego, asi que solo cuenta la protegida si es la que de verdad sale en ese chunk.
 * - Se aplica al generar (ChunkGenerator.tryGenerateStructure) y al buscar (StructureCheck, usado por /locate, las
 *   brujulas y los mapas), para que una estructura bloqueada no se senale donde no esta.
 * - Las cajas de las protegidas se guardan en memoria (una por posible inicio), asi cada una se calcula una sola vez.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProtectedStructureSpacing {

    private static final Logger LOGGER = LogManager.getLogger("bte_optimization_fixes");

    /** Bloques minimos entre la caja de una protegida y la de cualquier otra estructura. */
    public static final int MARGIN = 8;
    /** Hasta cuantos chunks se buscan inicios de protegidas alrededor de la otra estructura. */
    private static final int SEARCH_CHUNKS = 16;

    private static volatile Set<ResourceLocation> protectedIds;
    private static final Map<ChunkGenerator, GeneratorState> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    /** Lo que hace falta para generar una estructura fuera de su sitio normal. */
    public record Context(RegistryAccess registryAccess, ChunkGenerator generator, RandomState randomState,
                          StructureTemplateManager templateManager, LevelHeightAccessor heightAccessor, long seed) {
    }

    /** Estructura que esta intentando generar ChunkGenerator.tryGenerateStructure en este hilo. */
    public record Pending(Structure structure, Context context) {
    }

    public static final ThreadLocal<Pending> TRY_GENERATE = new ThreadLocal<>();
    public static final ThreadLocal<Pending> LOCATE = new ThreadLocal<>();

    private ProtectedStructureSpacing() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        protectedIds = null;
        STATES.clear();
    }

    /**
     * true si la estructura (no protegida) que ocuparia la caja dada se pegaria a una protegida.
     *
     * @param origin chunk donde empezaria, para buscar protegidas cerca antes de calcular su caja
     * @param box    caja de la estructura; solo se calcula si hay alguna protegida cerca
     */
    public static boolean isBlocked(Structure structure, ChunkPos origin, Supplier<BoundingBox> box, Context ctx) {
        try {
            Set<ResourceLocation> ids = protectedIds();
            if (ids.isEmpty()) return false;
            ResourceLocation id = ctx.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY).getKey(structure);
            if (id == null || ids.contains(id)) return false;
            GeneratorState state = state(ctx, ids);
            if (state.sets.isEmpty()) return false;

            List<BoundingBox> near = new ArrayList<>();
            for (ProtectedSet set : state.sets) set.collectNear(origin, ctx, near);
            if (near.isEmpty()) return false;

            BoundingBox own = box.get();
            if (own == null) return false;
            for (BoundingBox other : near) {
                if (other.inflatedBy(MARGIN).intersects(own)) return true;
            }
            return false;
        } catch (RuntimeException e) {
            LOGGER.warn("[bteopt] Fallo al comprobar estructuras protegidas cerca de {}: {}", origin, e.toString());
            return false;
        }
    }

    private static GeneratorState state(Context ctx, Set<ResourceLocation> ids) {
        synchronized (STATES) {
            GeneratorState state = STATES.get(ctx.generator());
            if (state == null) {
                state = new GeneratorState(ctx, ids);
                STATES.put(ctx.generator(), state);
            }
            return state;
        }
    }

    private static Set<ResourceLocation> protectedIds() {
        Set<ResourceLocation> ids = protectedIds;
        if (ids != null) return ids;
        synchronized (ProtectedStructureSpacing.class) {
            if (protectedIds != null) return protectedIds;
            Set<ResourceLocation> read = new HashSet<>();
            Path file = FMLPaths.CONFIGDIR.get().resolve("protectyourstructures.json");
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("structures")) {
                        for (JsonElement e : json.getAsJsonArray("structures")) {
                            ResourceLocation rl = ResourceLocation.tryParse(e.getAsString());
                            if (rl != null) read.add(rl);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("[bteopt] No se pudo leer {}: {}", file, e.toString());
                }
            }
            protectedIds = Collections.unmodifiableSet(read);
            LOGGER.info("[bteopt] Estructuras protegidas (sin otras estructuras pegadas): {}", read.size());
            return protectedIds;
        }
    }

    private static final class GeneratorState {
        final List<ProtectedSet> sets = new ArrayList<>();

        GeneratorState(Context ctx, Set<ResourceLocation> ids) {
            Registry<Structure> structures = ctx.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY);
            List<String> names = new ArrayList<>();
            ctx.generator().possibleStructureSets().forEach(holder -> {
                StructureSet set = holder.value();
                Set<Structure> prot = new HashSet<>();
                for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                    ResourceLocation id = structures.getKey(entry.structure().value());
                    if (id != null && ids.contains(id)) prot.add(entry.structure().value());
                }
                if (prot.isEmpty()) return;
                sets.add(new ProtectedSet(set, prot));
                names.add(holder.unwrapKey().map(k -> k.location().toString()).orElse("?"));
            });
            LOGGER.info("[bteopt] Sets con estructuras protegidas en esta dimension: {} {}", sets.size(), names);
        }
    }

    private static final class ProtectedSet {
        final StructureSet set;
        final Set<Structure> protectedStructures;
        /** Chunk de inicio posible -> caja de la estructura protegida (vacio si en ese sitio no sale una protegida). */
        final Map<Long, Optional<BoundingBox>> boxes = new ConcurrentHashMap<>();

        ProtectedSet(StructureSet set, Set<Structure> protectedStructures) {
            this.set = set;
            this.protectedStructures = protectedStructures;
        }

        void collectNear(ChunkPos origin, Context ctx, List<BoundingBox> out) {
            int minX = origin.x - SEARCH_CHUNKS, maxX = origin.x + SEARCH_CHUNKS;
            int minZ = origin.z - SEARCH_CHUNKS, maxZ = origin.z + SEARCH_CHUNKS;
            if (set.placement() instanceof RandomSpreadStructurePlacement spread) {
                // Un posible inicio por region de spacing x spacing chunks: se calculan directamente
                int spacing = spread.spacing();
                for (int rx = Math.floorDiv(minX, spacing); rx <= Math.floorDiv(maxX, spacing); rx++) {
                    for (int rz = Math.floorDiv(minZ, spacing); rz <= Math.floorDiv(maxZ, spacing); rz++) {
                        ChunkPos cand = spread.getPotentialStructureChunk(ctx.seed(), rx * spacing, rz * spacing);
                        if (cand.x < minX || cand.x > maxX || cand.z < minZ || cand.z > maxZ) continue;
                        if (cand.x == origin.x && cand.z == origin.z) continue;
                        boxAt(cand, ctx).ifPresent(out::add);
                    }
                }
            } else {
                // Otros tipos de colocacion (p. ej. la de Structure Gel que usa Dungeons Enhanced): chunk a chunk,
                // igual que hace vanilla con las exclusion_zone
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (x == origin.x && z == origin.z) continue;
                        if (!set.placement().isStructureChunk(ctx.generator(), ctx.randomState(), ctx.seed(), x, z)) continue;
                        boxAt(new ChunkPos(x, z), ctx).ifPresent(out::add);
                    }
                }
            }
        }

        Optional<BoundingBox> boxAt(ChunkPos cand, Context ctx) {
            long key = cand.toLong();
            Optional<BoundingBox> cached = boxes.get(key);
            if (cached != null) return cached;
            Optional<BoundingBox> result = Optional.empty();
            if (set.placement().isStructureChunk(ctx.generator(), ctx.randomState(), ctx.seed(), cand.x, cand.z)) {
                StructureStart start = simulate(cand, ctx);
                if (start != null) result = Optional.of(start.getBoundingBox());
            }
            boxes.put(key, result);
            return result;
        }

        /**
         * Repite la eleccion de ChunkGenerator.createStructures para este set en ese chunk: con una sola estructura, esa;
         * con varias (p. ej. dungeons_arise:major_structures), sorteo por peso con WorldgenRandom.setLargeFeatureSeed y,
         * si la elegida no sale ahi, se quita y se sortea otra vez. Devuelve el inicio solo si la que sale es protegida.
         */
        StructureStart simulate(ChunkPos cand, Context ctx) {
            List<StructureSet.StructureSelectionEntry> entries = set.structures();
            if (entries.size() == 1) return protectedStart(entries.get(0).structure().value(), cand, ctx);
            List<StructureSet.StructureSelectionEntry> list = new ArrayList<>(entries);
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(ctx.seed(), cand.x, cand.z);
            int total = 0;
            for (StructureSet.StructureSelectionEntry entry : list) total += entry.weight();
            while (!list.isEmpty()) {
                int roll = random.nextInt(total);
                int index = 0;
                for (StructureSet.StructureSelectionEntry entry : list) {
                    roll -= entry.weight();
                    if (roll < 0) break;
                    index++;
                }
                StructureSet.StructureSelectionEntry picked = list.get(index);
                StructureStart start = generate(picked.structure().value(), cand, ctx);
                if (start != null) return protectedStructures.contains(picked.structure().value()) ? start : null;
                list.remove(index);
                total -= picked.weight();
            }
            return null;
        }

        StructureStart protectedStart(Structure structure, ChunkPos cand, Context ctx) {
            return protectedStructures.contains(structure) ? generate(structure, cand, ctx) : null;
        }

        static StructureStart generate(Structure structure, ChunkPos cand, Context ctx) {
            try {
                StructureStart start = structure.generate(ctx.registryAccess(), ctx.generator(),
                        ctx.generator().getBiomeSource(), ctx.randomState(), ctx.templateManager(), ctx.seed(),
                        cand, 0, ctx.heightAccessor(), structure.biomes()::contains);
                return start.isValid() ? start : null;
            } catch (RuntimeException e) {
                LOGGER.warn("[bteopt] No se pudo calcular una estructura en {}: {}", cand, e.toString());
                return null;
            }
        }
    }
}
