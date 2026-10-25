package com.endersjourney.bteoptimizationfixes.worldgen;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Reutilizar el NoiseChunk de un chunk aunque el chunk se haya descargado y vuelto a cargar (ver WorldgenOpt, punto 3).
 *
 * Vanilla crea el NoiseChunk de un chunk en la fase biomes y lo guarda en el propio chunk (ChunkAccess.noiseChunk), y
 * las fases noise, surface y carvers lo reutilizan. Pero ese campo no se guarda en disco: si el chunk se descarga a
 * medio generar y se vuelve a cargar (Chunky lo hace con casi todos), la siguiente fase crea otro desde cero. En el End
 * eso cuesta ~12 ms por chunk. Aquí se guardan los últimos NoiseChunk creados, por generador de biomas/semilla
 * (RandomState) y posición, y si el chunk vuelve sin el suyo se le devuelve el mismo objeto que habría tenido de no
 * haberse descargado. Es exactamente lo que hace vanilla cuando no hay descarga.
 *
 * Solo para chunks sin mezcla con chunks antiguos (Blender vacío), que es el caso de cualquier mundo nuevo.
 */
public final class NoiseChunkCache {

    /** Máximo de NoiseChunk guardados por dimensión. Cada uno ocupa pocos KB. */
    private static final int CAPACITY = 4096;

    private static final Map<RandomState, LinkedHashMap<Long, NoiseChunk>> BY_STATE = new WeakHashMap<>();

    private NoiseChunkCache() {
    }

    private static LinkedHashMap<Long, NoiseChunk> mapFor(RandomState state) {
        return BY_STATE.computeIfAbsent(state, k -> new LinkedHashMap<>(1024, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, NoiseChunk> eldest) {
                return size() > CAPACITY;
            }
        });
    }

    public static synchronized NoiseChunk get(RandomState state, ChunkPos pos) {
        LinkedHashMap<Long, NoiseChunk> map = BY_STATE.get(state);
        return map == null ? null : map.get(pos.toLong());
    }

    public static synchronized void put(RandomState state, ChunkPos pos, NoiseChunk noiseChunk) {
        mapFor(state).put(pos.toLong(), noiseChunk);
    }

    public static synchronized void clear() {
        BY_STATE.clear();
    }
}
