package com.endersjourney.bteoptimizationfixes.mixin.quark;

import com.endersjourney.bteoptimizationfixes.debug.GenTiming;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Herramienta de medición (ver GenTiming): tiempo de cada generador de Quark (grupos de piedra, tipos de piedra nuevos,
 * espirales del End...). Todos pasan por Generator.generate, dentro de la feature quark:deferred_feature_*, que en el
 * Overworld es una de las más caras. Se apunta como detalle "Quark: <clase del generador>".
 */
@Pseudo
@Mixin(targets = "vazkii.quark.base.world.generator.Generator", remap = false)
public abstract class QuarkGeneratorTimingMixin {

    @Inject(method = "generate", at = @At("HEAD"), remap = false)
    private void bteopt$start(CallbackInfoReturnable<Integer> cir) {
        if (GenTiming.enabled) GenTiming.detailStart("Quark: " + this.getClass().getSimpleName());
    }

    @Inject(method = "generate", at = @At("RETURN"), remap = false)
    private void bteopt$end(CallbackInfoReturnable<Integer> cir) {
        GenTiming.end();
    }
}
