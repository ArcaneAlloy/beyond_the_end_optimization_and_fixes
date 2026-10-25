package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.debug.GenTiming;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Herramienta de medición (ver GenTiming): tiempo de cada feature colocada.
 * m_226368_ = PlacedFeature.placeWithContext(PlacementContext, RandomSource, BlockPos), por donde pasan tanto
 * place() como placeWithBiomeCheck(). Nombre SRG sacado de los volcados de hilos del modpack
 * (PlacedFeature.m_226368_ en PlacedFeature.java:55, dentro de la fase de features).
 */
@Mixin(value = PlacedFeature.class, remap = false)
public abstract class PlacedFeatureTimingMixin {

    @Inject(method = "m_226368_", at = @At("HEAD"), remap = false)
    private void bteopt$start(CallbackInfoReturnable<Boolean> cir) {
        GenTiming.featureStart(this);
    }

    @Inject(method = "m_226368_", at = @At("RETURN"), remap = false)
    private void bteopt$end(CallbackInfoReturnable<Boolean> cir) {
        GenTiming.end();
    }
}
