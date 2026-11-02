package com.endersjourney.bteoptimizationfixes.mixin.mutantmore;

import com.endersjourney.bteoptimizationfixes.compat.mutantmore.MutationCloudTracker;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mutant More 2.0.0: MiscUtils.mutationCloudForMob(mob) busca las nubes de mutación que tocan a la entidad (en cada
 * tick de cada entidad viva y en cada frame al dibujarla) y devuelve la que la tiene como objetivo, o null.
 *
 * Si en el nivel no hay ninguna nube (lo normal), se devuelve null sin buscar: es el mismo resultado. Ver
 * MutationCloudTracker. Sin Mutant More el Mixin no se aplica (@Pseudo).
 */
@Pseudo
@Mixin(targets = "com.alexander.mutantmore.util.MiscUtils", remap = false)
public abstract class MutationCloudSkipMixin {

    @Inject(method = "mutationCloudForMob", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ejfixes$skipWithoutClouds(LivingEntity entity, CallbackInfoReturnable<Object> cir) {
        if (MutationCloudTracker.noClouds(entity.level)) cir.setReturnValue(null);
    }
}
