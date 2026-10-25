package com.endersjourney.bteoptimizationfixes.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * Cachés pequeñas para funciones puras de la generación (ver WorldgenOpt, puntos 4 y 5). Son tablas de tamaño fijo
 * indexadas por posición: cada casilla guarda un objeto inmutable con la posición y el valor, así que varios hilos
 * pueden leer y escribir a la vez sin cerrojos; si dos posiciones caen en la misma casilla, la última pisa a la otra y
 * simplemente se vuelve a calcular. Nunca se devuelve un valor de otra posición.
 */
public final class PureValueCaches {

    private PureValueCaches() {
    }

    private static int mix(long h) {
        h *= 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }

    /** Altura de las islas del End (EndIslandDensityFunction.getHeightValue) por (x / 8, z / 8). */
    public static final class EndIslandHeights {
        private static final int SIZE = 1 << 14;
        private final Entry[] table = new Entry[SIZE];

        private record Entry(int x, int z, float value) {
        }

        /** Devuelve el valor guardado, o NaN si no está. */
        public float get(int x, int z) {
            Entry e = table[mix(((long) x << 32) ^ (z & 0xFFFFFFFFL)) & (SIZE - 1)];
            return e != null && e.x == x && e.z == z ? e.value : Float.NaN;
        }

        public void put(int x, int z, float value) {
            table[mix(((long) x << 32) ^ (z & 0xFFFFFFFFL)) & (SIZE - 1)] = new Entry(x, z, value);
        }
    }

    /** Bioma "sin chunk" (ServerLevel.getUncachedNoiseBiome) por posición en cuartos de bloque. */
    public static final class UncachedBiomes {
        private static final int SIZE = 1 << 15;
        private final Entry[] table = new Entry[SIZE];

        private record Entry(int x, int y, int z, Holder<Biome> biome) {
        }

        private static int index(int x, int y, int z) {
            return mix((((long) x * 0x1F1F1F1FL) ^ ((long) y << 21)) * 31L + z) & (SIZE - 1);
        }

        public Holder<Biome> get(int x, int y, int z) {
            Entry e = table[index(x, y, z)];
            return e != null && e.x == x && e.y == y && e.z == z ? e.biome : null;
        }

        public void put(int x, int y, int z, Holder<Biome> biome) {
            table[index(x, y, z)] = new Entry(x, y, z, biome);
        }
    }
}
