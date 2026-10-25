package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.debug.GenTiming;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Herramienta de medición (ver GenTiming): cálculo de la altura preliminar de la superficie.
 * m_198249_ = NoiseChunk.computePreliminarySurfaceLevel(long): baja por la columna evaluando la densidad inicial hasta
 * encontrar suelo. Se calcula una vez por columna de 4x4 bloques y se guarda en caché; la usan las reglas de
 * superficie, los acuíferos y algunas features. Nombre SRG sacado de las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(value = NoiseChunk.class, remap = false)
public abstract class PreliminarySurfaceTimingMixin {

    @Inject(method = "m_198249_", at = @At("HEAD"), remap = false)
    private void bteopt$start(CallbackInfoReturnable<Integer> cir) {
        GenTiming.detailStart(GenTiming.PRELIMINARY_SURFACE);
    }

    @Inject(method = "m_198249_", at = @At("RETURN"), remap = false)
    private void bteopt$end(CallbackInfoReturnable<Integer> cir) {
        GenTiming.end();
    }
}
