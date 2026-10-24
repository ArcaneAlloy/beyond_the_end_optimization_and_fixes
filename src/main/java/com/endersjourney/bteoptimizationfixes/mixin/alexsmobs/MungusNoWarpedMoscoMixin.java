package com.endersjourney.bteoptimizationfixes.mixin.alexsmobs;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * El Mungus lleno de hongos distorsionados ya no sirve para transformar un Crimson Mosquito en Warped Mosco (el
 * mosquito ya no le hace el dano extra ni le quita la explosion). Ver CrimsonMosquitoNoWarpedTransformMixin.
 *
 * Verificado por descompilacion de alexsmobs-1.21.1.jar: isWarpedMoscoReady solo lo usa EntityCrimsonMosquito.
 */
@Pseudo
@Mixin(targets = "com.github.alexthe666.alexsmobs.entity.EntityMungus", remap = false)
public abstract class MungusNoWarpedMoscoMixin {

    @Inject(method = "isWarpedMoscoReady", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$neverReady(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
