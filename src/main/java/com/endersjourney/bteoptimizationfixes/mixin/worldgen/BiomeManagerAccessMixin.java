package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.worldgen.BiomeManagerAccess;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Hace que BiomeManager implemente BiomeManagerAccess, para que la caché de biomas de la superficie pueda leer la
 * fuente de biomas y la semilla del zoom. f_47862_ = noiseBiomeSource, f_47863_ = biomeZoomSeed. Nombres SRG sacados
 * de las tablas de mapeo de Forge 1.19.2 (con @Shadow, como el resto de mixins de este mod; @Accessor no sirve aquí
 * porque el procesador de anotaciones lo comprueba contra los nombres de desarrollo).
 */
@Mixin(value = BiomeManager.class, remap = false)
public abstract class BiomeManagerAccessMixin implements BiomeManagerAccess {

    @Shadow(remap = false)
    @Final
    private BiomeManager.NoiseBiomeSource f_47862_;

    @Shadow(remap = false)
    @Final
    private long f_47863_;

    @Override
    public BiomeManager.NoiseBiomeSource bteopt$getNoiseBiomeSource() {
        return this.f_47862_;
    }

    @Override
    public long bteopt$getBiomeZoomSeed() {
        return this.f_47863_;
    }
}
