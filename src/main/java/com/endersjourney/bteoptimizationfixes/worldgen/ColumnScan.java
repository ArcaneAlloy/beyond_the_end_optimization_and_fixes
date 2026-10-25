package com.endersjourney.bteoptimizationfixes.worldgen;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;

/**
 * Utilidades del escaneo vertical rápido (ver WorldgenOpt, punto 7).
 *
 * Idea: ProtoChunk.getBlockState y LevelChunk.getBlockState devuelven exactamente Blocks.AIR para cualquier posición
 * de una sección marcada como "solo aire" (hasOnlyAir) dentro de la altura del mundo. Así que, mientras un escaneo
 * vertical recorre secciones de solo aire, todas las lecturas valen AIR y se pueden saltar de golpe hasta el borde de
 * la sección, con el mismo resultado que leyendo bloque a bloque.
 */
public final class ColumnScan {

    private ColumnScan() {
    }

    /**
     * La sección de la posición si el chunk está en la región, es de un tipo conocido (ProtoChunk, LevelChunk o
     * ImposterProtoChunk, cuyas lecturas siguen la regla de arriba), la altura está dentro del mundo y la sección es de
     * solo aire. Si no, null.
     */
    public static LevelChunkSection airSection(WorldGenRegion region, int x, int y, int z) {
        if (region.isOutsideBuildHeight(y)) return null;
        int cx = x >> 4, cz = z >> 4;
        if (!region.hasChunk(cx, cz)) return null;
        ChunkAccess chunk = region.getChunk(cx, cz);
        Class<?> k = chunk.getClass();
        if (k != ProtoChunk.class && k != LevelChunk.class && k != ImposterProtoChunk.class) return null;
        int index = chunk.getSectionIndex(y);
        LevelChunkSection[] sections = chunk.getSections();
        if (index < 0 || index >= sections.length) return null;
        LevelChunkSection section = sections[index];
        return section != null && section.hasOnlyAir() ? section : null;
    }

    /**
     * Cuántos pasos más (en la dirección dy = +1 o -1) puede moverse la posición (x, y, z) sin que ninguna de las
     * posiciones posición + offset salga de su sección de solo aire. -1 si alguna no está ya en una sección de solo aire.
     * Sin offsets, Integer.MAX_VALUE.
     */
    public static int airRun(WorldGenRegion region, int x, int y, int z, int[] offsets, int dy) {
        int run = Integer.MAX_VALUE;
        for (int i = 0; i < offsets.length; i += 3) {
            int ly = y + offsets[i + 1];
            if (airSection(region, x + offsets[i], ly, z + offsets[i + 2]) == null) return -1;
            int steps = dy > 0 ? 15 - (ly & 15) : (ly & 15);
            if (steps < run) run = steps;
        }
        return run;
    }
}
