package com.endersjourney.bteoptimizationfixes.mixin.dungeonsgear;

import com.endersjourney.bteoptimizationfixes.compat.dungeonsgear.UnbreakableArtifacts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Artefactos de Dungeons Gear irrompibles (ver UnbreakableArtifacts): durabilidad maxima 0 al crearse el artefacto
 * (el constructor le pone 64) y al final de reload(), que le vuelve a poner la durabilidad de su config.
 *
 * Verificado por desensamblado de dungeons_libraries-1.19.2-3.0.12-beta-all.jar (ArtifactItem: un solo constructor
 * (Item.Properties) y reload()V).
 */
@Pseudo
@Mixin(targets = "com.infamous.dungeons_libraries.items.artifacts.ArtifactItem", remap = false)
public abstract class ArtifactUnbreakableMixin {

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void bteopt$unbreakableOnCreate(CallbackInfo ci) {
        UnbreakableArtifacts.apply(this);
    }

    @Inject(method = "reload", at = @At("TAIL"), remap = false)
    private void bteopt$unbreakableOnReload(CallbackInfo ci) {
        UnbreakableArtifacts.apply(this);
    }
}
