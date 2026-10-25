package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.debug.GenTiming;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Herramienta de medición (ver GenTiming): aplicar las reglas de superficie de un chunk.
 * m_224648_ = SurfaceSystem.buildSurface(...): recorre las 256 columnas de arriba abajo y aplica la regla de superficie
 * de la dimensión (Terralith, Tectonic...). Lo que quede de la fase surface fuera de esto es preparación (crear el
 * NoiseChunk si no existe, etc.). Nombre SRG sacado de las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(value = SurfaceSystem.class, remap = false)
public abstract class SurfaceRulesTimingMixin {

    @Inject(method = "m_224648_", at = @At("HEAD"), remap = false)
    private void bteopt$start(CallbackInfo ci) {
        GenTiming.surfaceRulesStart();
    }

    @Inject(method = "m_224648_", at = @At("RETURN"), remap = false)
    private void bteopt$end(CallbackInfo ci) {
        GenTiming.surfaceRulesEnd();
    }
}
