package com.endersjourney.bteoptimizationfixes.worldgen;

import net.minecraft.world.level.biome.BiomeManager;

/**
 * Acceso a los dos campos de BiomeManager que necesita la caché de biomas de la superficie (SurfaceBiomeCache).
 * Lo implementa en BiomeManager el mixin worldgen.BiomeManagerAccessMixin.
 */
public interface BiomeManagerAccess {

    BiomeManager.NoiseBiomeSource bteopt$getNoiseBiomeSource();

    long bteopt$getBiomeZoomSeed();
}
