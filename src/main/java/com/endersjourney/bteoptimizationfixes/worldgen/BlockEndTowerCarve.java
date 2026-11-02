package com.endersjourney.bteoptimizationfixes.worldgen;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Excava el interior de las torres de BlockEnd (ver BlockEndTowerAirMixin).
 */
public final class BlockEndTowerCarve {

    private BlockEndTowerCarve() {
    }

    private static final int BATCH = 400;

    public static void queueCarve(Queue<Runnable> tasks, ServerLevel level, ResourceLocation location, int addX, int y, int addZ) {
        if (location == null || !"blockend".equals(location.getNamespace())) return;
        String path = location.getPath();
        if (!path.equals("tower0") && !path.equals("tower1")) return;

        try {
            StructureTemplate template = level.getServer().getStructureManager().get(location).orElse(null);
            if (template == null) return;

            // Mismo origen y misma paleta que BlockEnd#placeComponentBatched
            Vec3i size = template.getSize();
            BlockPos origin = new BlockPos(-size.getX() / 2 + addX, y, -size.getZ() / 2 + addZ);
            StructurePlaceSettings settings = new StructurePlaceSettings();
            List<StructureTemplate.StructureBlockInfo> blocks =
                    settings.getRandomPalette(palettes(template), origin).blocks();

            // Bloque solido mas bajo y mas alto de cada columna de la plantilla
            Map<Long, int[]> range = new HashMap<>();
            for (StructureTemplate.StructureBlockInfo info : blocks) {
                if (info.state.isAir()) continue;
                long key = BlockPos.asLong(info.pos.getX(), 0, info.pos.getZ());
                int[] r = range.computeIfAbsent(key, k -> new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE});
                r[0] = Math.min(r[0], info.pos.getY());
                r[1] = Math.max(r[1], info.pos.getY());
            }

            List<BlockPos> carve = new ArrayList<>();
            for (StructureTemplate.StructureBlockInfo info : blocks) {
                if (!info.state.isAir()) continue;
                int[] r = range.get(BlockPos.asLong(info.pos.getX(), 0, info.pos.getZ()));
                if (r == null || info.pos.getY() <= r[0] || info.pos.getY() >= r[1]) continue;
                carve.add(StructureTemplate.calculateRelativePosition(settings, info.pos).offset(origin));
            }

            for (int i = 0; i < carve.size(); i += BATCH) {
                int start = i;
                int end = Math.min(i + BATCH, carve.size());
                tasks.add(() -> {
                    for (int j = start; j < end; j++) {
                        level.setBlock(carve.get(j), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                });
            }
            BTEOptimizationFixes.LOGGER.info("[{}] BlockEnd {}: {} bloques de aire interior a excavar",
                    BTEOptimizationFixes.MODID, location, carve.size());
        } catch (Throwable t) {
            BTEOptimizationFixes.LOGGER.warn("[{}] No se pudo calcular el interior de {}; se coloca como antes",
                    BTEOptimizationFixes.MODID, location, t);
        }
    }

    /** StructureTemplate#palettes es privado: f_74482_ en produccion, palettes en desarrollo. */
    @SuppressWarnings("unchecked")
    private static List<StructureTemplate.Palette> palettes(StructureTemplate template) throws ReflectiveOperationException {
        Field f;
        try {
            f = StructureTemplate.class.getDeclaredField("f_74482_");
        } catch (NoSuchFieldException e) {
            f = StructureTemplate.class.getDeclaredField("palettes");
        }
        f.setAccessible(true);
        return (List<StructureTemplate.Palette>) f.get(template);
    }
}
