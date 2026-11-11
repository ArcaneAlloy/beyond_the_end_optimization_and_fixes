package com.endersjourney.bteoptimizationfixes.compat.mimic;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Mimic 1.2.38 solo pone mimics en los cofres que se crean con StructurePiece.createChest (estructuras vanilla
 * antiguas), en el Iglu, la End City y las mazmorras vanilla. Las estructuras de plantillas (casi todas las de mods)
 * traen el cofre dentro de la plantilla y el mod nunca se entera.
 *
 * Al terminar de colocar una estructura en un chunk (StructureStart.placeInChunk), se buscan los cofres normales con
 * tabla de botin de ese chunk. Cada uno tiene 1 de cada MimicConfig.template_chest_chance de ser mimic, con un maximo
 * de MimicConfig.max_mimics_per_structure por estructura (la cuenta se guarda con el mundo, MimicTemplateData). Los
 * cofres importantes (ImportantChests) se saltan antes de tirar, asi no gastan plaza.
 *
 * Al elegido se le pone encima el bloque marcador del mod (si encima hay aire, enredadera o un bloque de estructura),
 * marcado como "forzado": el mod Mimic ya no vuelve a tirar su propia probabilidad y el mimic sale seguro (ver
 * MimicSpawnerImportantChestMixin). El resto lo hace el mod como siempre: el mimic se come el cofre.
 *
 * Las estructuras que ya tienen mimics por el propio mod (vanilla y Dungeons Enhanced) no cambian: su cofre ya tiene el
 * marcador encima y aqui se salta.
 *
 * Solo cofres normales (minecraft:chest y el cofre de Lootr): el mod no sabe convertir barriles ni cofres trampa.
 * Las estructuras de MimicConfig.excluded_structures no se tocan.
 */
public final class MimicTemplateChests {

    private static final ResourceLocation LOOTR_CHEST = new ResourceLocation("lootr", "lootr_chest");

    private static volatile Block marker;
    private static volatile boolean resolved;

    private MimicTemplateChests() {
    }

    /** El bloque marcador del mod Mimic (Constants.getMimicSpawnBlock()), o null sin el mod. */
    private static Block marker() {
        if (!resolved) {
            synchronized (MimicTemplateChests.class) {
                if (!resolved) {
                    if (ModList.get().isLoaded("mimic")) {
                        try {
                            Method m = Class.forName("ca.lukegrahamlandry.mimic.Constants").getMethod("getMimicSpawnBlock");
                            marker = (Block) m.invoke(null);
                        } catch (Throwable t) {
                            BTEOptimizationFixes.LOGGER.warn("[bteopt] No se encontro el bloque marcador de Mimic: {}", t.toString());
                        }
                    }
                    resolved = true;
                }
            }
        }
        return marker;
    }

    public static void afterPlaceInChunk(WorldGenLevel level, Structure structure, ChunkPos start, BoundingBox box, ChunkPos chunkPos) {
        Block mark = marker();
        if (mark == null) return;
        ResourceLocation id = level.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY).getKey(structure);
        if (id != null && MimicConfig.get().excludedStructures.contains(id)) return;
        ServerLevel serverLevel = level.getLevel();
        MimicTemplateData data = MimicTemplateData.get(serverLevel);
        MimicConfig config = MimicConfig.get();
        if (data == null || config.maxMimicsPerStructure <= 0) return;
        ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z);
        List<BlockPos> chests = new ArrayList<>();
        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
            if (!box.isInside(pos)) continue;
            BlockState state = chunk.getBlockState(pos);
            if (!isPlainChest(state)) continue;
            chests.add(pos.immutable());
        }
        if (chests.isEmpty()) return;
        String key = (id != null ? id.toString() : "?") + "|" + start.x + "|" + start.z;
        RandomSource random = level.getRandom();
        for (BlockPos pos : chests) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null || !be.saveWithoutMetadata().contains("LootTable", Tag.TAG_STRING)) continue;
            BlockPos above = pos.above();
            if (!box.isInside(above)) continue;
            BlockState up = level.getBlockState(above);
            if (!(up.isAir() || up.is(Blocks.VINE) || up.is(Blocks.STRUCTURE_BLOCK))) continue;
            if (ImportantChests.isImportant(serverLevel.getServer(), be)) continue;
            if (random.nextInt(config.templateChestChance) != 0) continue;
            if (!data.tryReserve(key, config.maxMimicsPerStructure, above.asLong())) return;
            level.setBlock(above, mark.defaultBlockState(), 3);
            level.scheduleTick(above, mark, 1);
        }
    }

    private static boolean isPlainChest(BlockState state) {
        if (state.is(Blocks.CHEST)) return true;
        return LOOTR_CHEST.equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
    }
}
