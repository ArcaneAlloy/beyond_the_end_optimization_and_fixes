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
 * Excava el interior de las torres de BlockEnd (ver BlockEndTowerAirMixin) y abre una hondonada alrededor de su base.
 *
 * Las torres se colocan a una altura fija pensada para el End vanilla. Con el terreno de Nullscape, mas alto, la
 * base de la torre (y la sala del ascensor) queda por debajo de la superficie: aunque se excave el interior, el
 * ascensor queda metido en un pozo de end stone. Por eso, ademas, se quita el end stone que haya por encima de la
 * base en la planta de la torre y, alrededor, con una pendiente de 1 bloque de altura por bloque de distancia
 * (hasta CRATER_RADIUS): queda una hondonada con taludes en vez de paredes rectas. Solo se quita end stone.
 */
public final class BlockEndTowerCarve {

    private BlockEndTowerCarve() {
    }

    private static final int BATCH = 400;
    /** Distancia maxima (en bloques) de la pendiente alrededor de la planta de la torre. */
    private static final int CRATER_RADIUS = 20;
    /** Altura maxima por encima de la base que se revisa al abrir la hondonada. */
    private static final int CRATER_HEIGHT = 48;
    /** Columnas revisadas por tarea. */
    private static final int CRATER_COLUMNS = 48;

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

            queueCrater(tasks, level, blocks, settings, origin);

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

    /**
     * Planta de la torre = columnas con algun bloque solido en las dos primeras capas de la plantilla. Para cada
     * columna a menos de CRATER_RADIUS de la planta, se quita el end stone por encima de base + distancia.
     */
    private static void queueCrater(Queue<Runnable> tasks, ServerLevel level, List<StructureTemplate.StructureBlockInfo> blocks,
                                    StructurePlaceSettings settings, BlockPos origin) {
        List<int[]> footprint = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (StructureTemplate.StructureBlockInfo info : blocks) {
            if (info.state.isAir() || info.pos.getY() > 1) continue;
            BlockPos w = StructureTemplate.calculateRelativePosition(settings, info.pos).offset(origin);
            if (seen.add(BlockPos.asLong(w.getX(), 0, w.getZ()))) footprint.add(new int[]{w.getX(), w.getZ()});
        }
        if (footprint.isEmpty()) return;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] f : footprint) {
            minX = Math.min(minX, f[0]); maxX = Math.max(maxX, f[0]);
            minZ = Math.min(minZ, f[1]); maxZ = Math.max(maxZ, f[1]);
        }

        int baseY = origin.getY();
        List<int[]> columns = new ArrayList<>(); // {x, z, primera y a vaciar}
        for (int x = minX - CRATER_RADIUS; x <= maxX + CRATER_RADIUS; x++) {
            for (int z = minZ - CRATER_RADIUS; z <= maxZ + CRATER_RADIUS; z++) {
                double best = Double.MAX_VALUE;
                for (int[] f : footprint) {
                    double dx = x - f[0], dz = z - f[1];
                    double d = dx * dx + dz * dz;
                    if (d < best) best = d;
                }
                double dist = Math.sqrt(best);
                if (dist > CRATER_RADIUS) continue;
                columns.add(new int[]{x, z, baseY + 1 + (int) Math.ceil(dist)});
            }
        }

        int top = baseY + CRATER_HEIGHT;
        for (int i = 0; i < columns.size(); i += CRATER_COLUMNS) {
            int start = i;
            int end = Math.min(i + CRATER_COLUMNS, columns.size());
            tasks.add(() -> {
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int j = start; j < end; j++) {
                    int[] c = columns.get(j);
                    for (int yy = c[2]; yy <= top; yy++) {
                        pos.set(c[0], yy, c[1]);
                        if (level.getBlockState(pos).is(Blocks.END_STONE)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            });
        }
        BTEOptimizationFixes.LOGGER.info("[{}] BlockEnd: hondonada de {} columnas alrededor de la torre (base y={})",
                BTEOptimizationFixes.MODID, columns.size(), baseY);
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
