package com.endersjourney.bteoptimizationfixes.mixin.alexsmobs;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * En el modpack el Warped Mosco solo se consigue mutando un Crimson Mosquito con Compound Z (Mutant More, ver
 * data/bte_optimization_fixes/mutation_type/warped_mosco.json). Se desactivan los metodos de Alex's Mobs:
 *  - picar a un Mungus lleno de hongos distorsionados (EntityMungus#isWarpedMoscoReady, ver MungusNoWarpedMoscoMixin),
 *  - picar a uno de los mobs de la config warpedMoscoMobTriggers (isNonMungusWarpedTrigger),
 *  - usar una Warped Mixture sobre el mosquito.
 * Los tres acaban en setSick(true) (el mosquito "enfermo" crece y se convierte en Warped Mosco); se ignora siempre.
 *
 * Verificado por descompilacion de alexsmobs-1.21.1.jar (EntityCrimsonMosquito).
 * @Pseudo + remap = false: sin Alex's Mobs el Mixin no se aplica.
 */
@Pseudo
@Mixin(targets = "com.github.alexthe666.alexsmobs.entity.EntityCrimsonMosquito", remap = false)
public abstract class CrimsonMosquitoNoWarpedTransformMixin {

    @Inject(method = "isNonMungusWarpedTrigger", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$noMobTriggers(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "setSick", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$neverSick(boolean sick, CallbackInfo ci) {
        if (sick) ci.cancel();
    }
}
