package com.endersjourney.bteoptimizationfixes.mixin.blockend;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Sustituye StructureManager.Structure#cleanAreaBatched (fr.shoqapik.blockend,
 * version 1.2.2) por una version que:
 *
 *  1) Ya no escanea bloque a bloque desde el primer momento. Recorre por
 *     CHUNK (16x16 columnas) y, dentro de cada chunk, por SECCION de 16
 *     niveles: si una seccion entera ya es aire (LevelChunkSection#hasOnlyAir),
 *     se salta sin llamar a getBlockState() ni una sola vez para sus 4096
 *     posiciones. El End, fuera de la isla central, es en su mayoria vacio,
 *     asi que esto deberia ahorrarse una parte importante de las ~4,37
 *     millones de lecturas de bloque que hacia el original.
 *
 *  2) Tanto el escaneo como la escritura quedan repartidos en la cola
 *     `tasks` que la propia clase ya usa para colocar estructuras (2 tareas
 *     por tick, via su tick() sin tocar). El metodo original solo repartia
 *     la escritura; el escaneo entero se hacia de un tiron antes de
 *     encolar nada. Ahora se encola una tarea de escaneo por chunk (256
 *     tareas para la rejilla -125..125), y cada una de esas tareas encola
 *     a su vez sus propios lotes de limpieza de 2000 bloques, igual que
 *     antes.
 *
 * El resultado visible en el mundo es identico al original: los mismos
 * bloques que antes se limpiaban a aire (todo lo que no sea end_stone
 * dentro del radio) se siguen limpiando; solo cambia como se reparte el
 * trabajo en el tiempo.
 *
 * AVISO: no se ha podido compilar contra las clases reales de Minecraft/
 * Forge en el entorno donde se escribio esto (no hay acceso al classpath
 * modding). Los nombres de metodo (getMinBuildHeight, getBlockState,
 * getSections, hasOnlyAir, setBlock, is) son los mapeados oficiales de
 * 1.19.2 y deberian remapearse solos al compilar con ForgeGradle igual
 * que el resto de mixins de ej_fixes, pero probadlo primero en un mundo
 * de prueba, no directamente en la partida real.
 */
@Pseudo
@Mixin(targets = "fr.shoqapik.blockend.structures.StructureManager$Structure", remap = false)
public abstract class BlockEndCleanAreaMixin {

    @Shadow(remap = false)
    public Queue<Runnable> tasks;

    private static final int MIN_X = -125;
    private static final int MAX_X = 125;
    private static final int MIN_Z = -125;
    private static final int MAX_Z = 125;
    private static final double RADIUS = 200.0;
    private static final int CLEAN_TOP_Y = 70;
    private static final int CLEAN_BATCH = 2000;

    /**
     * Antes: recogia aqui mismo, de forma sincrona, TODAS las posiciones a
     * limpiar (hasta ~4,37M comprobaciones getBlockState) y solo repartia
     * la escritura final. Ahora esto es practicamente instantaneo: solo
     * encola 256 tareas de escaneo por chunk, una cada una.
     */
    @Overwrite
    private void cleanAreaBatched(ServerLevel level) {
        int minY = level.getMinBuildHeight();

        int minChunkX = MIN_X >> 4;
        int maxChunkX = (MAX_X - 1) >> 4;
        int minChunkZ = MIN_Z >> 4;
        int maxChunkZ = (MAX_Z - 1) >> 4;

        for (int cxOuter = minChunkX; cxOuter <= maxChunkX; cxOuter++) {
            for (int czOuter = minChunkZ; czOuter <= maxChunkZ; czOuter++) {
                final int cx = cxOuter;
                final int cz = czOuter;
                this.tasks.add(() -> scanAndQueueChunk(level, cx, cz, minY));
            }
        }
    }

    /**
     * Escanea un unico chunk (16x16 columnas) y encola sus propios lotes de
     * limpieza. Se ejecuta ya dentro de tick() -> tasks.poll(), no de golpe.
     */
    private void scanAndQueueChunk(ServerLevel level, int cx, int cz, int minY) {
        int baseX = cx << 4;
        int baseZ = cz << 4;

        if (!chunkTouchesRadius(baseX, baseZ)) {
            return;
        }

        LevelChunk chunk = level.getChunk(cx, cz);
        LevelChunkSection[] sections = chunk.getSections();

        List<BlockPos> positions = new ArrayList<>();

        for (int y = minY; y < CLEAN_TOP_Y; y++) {
            if (((y - minY) & 15) == 0) {
                int sectionIndex = (y - minY) >> 4;
                LevelChunkSection section = (sectionIndex >= 0 && sectionIndex < sections.length)
                        ? sections[sectionIndex]
                        : null;

                if (section != null && section.hasOnlyAir()) {
                    // Seccion entera (16 niveles) ya vacia: nos la saltamos
                    // sin leer ni un solo bloque.
                    y = Math.min(y + 16, CLEAN_TOP_Y) - 1;
                    continue;
                }
            }

            for (int x = baseX; x < baseX + 16; x++) {
                if (x < MIN_X || x >= MAX_X) continue;

                for (int z = baseZ; z < baseZ + 16; z++) {
                    if (z < MIN_Z || z >= MAX_Z) continue;
                    if (!(Math.sqrt((double) x * x + (double) z * z) < RADIUS)) continue;

                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.END_STONE)) continue;

                    positions.add(pos);
                }
            }
        }

        queueCleanup(level, positions);
    }

    private void queueCleanup(ServerLevel level, List<BlockPos> positions) {
        for (int i = 0; i < positions.size(); i += CLEAN_BATCH) {
            int start = i;
            int end = Math.min(i + CLEAN_BATCH, positions.size());

            this.tasks.add(() -> {
                for (int j = start; j < end; j++) {
                    level.setBlock(positions.get(j), Blocks.AIR.defaultBlockState(), 1);
                }
            });
        }
    }

    private static boolean chunkTouchesRadius(int baseX, int baseZ) {
        for (int dx = 0; dx < 16; dx++) {
            int x = baseX + dx;
            if (x < MIN_X || x >= MAX_X) continue;

            for (int dz = 0; dz < 16; dz++) {
                int z = baseZ + dz;
                if (z < MIN_Z || z >= MAX_Z) continue;

                if (Math.sqrt((double) x * x + (double) z * z) < RADIUS) {
                    return true;
                }
            }
        }
        return false;
    }
}
