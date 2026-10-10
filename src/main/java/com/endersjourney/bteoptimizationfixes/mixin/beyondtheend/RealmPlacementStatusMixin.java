package com.endersjourney.bteoptimizationfixes.mixin.beyondtheend;

import com.endersjourney.bteoptimizationfixes.loading.WorldLoadingStatus;
import com.endersjourney.bteoptimizationfixes.loading.WorldLoadingStatus.Phase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Beyond The End 1.8.0: FallenDragonFight$Structure#place() solo se llama
 * desde verify() mientras el reino no esta colocado, es decir, una vez por
 * mundo, en el primer tick del servidor. Solo marca la fase para la
 * pantalla de carga.
 *
 * Verificado por desensamblado del jar real (beyondtheend-1.8.0.jar).
 */
@Pseudo
@Mixin(targets = "com.TBK.beyondtheend.server.entity.FallenDragonFight$Structure", remap = false)
public abstract class RealmPlacementStatusMixin {

    @Inject(method = "place()V", at = @At("HEAD"), remap = false)
    private void bteopt$placeStart(CallbackInfo ci) {
        WorldLoadingStatus.set(Phase.BEYONDTHEEND_REALM);
    }

    @Inject(method = "place()V", at = @At("RETURN"), remap = false)
    private void bteopt$placeEnd(CallbackInfo ci) {
        WorldLoadingStatus.set(Phase.FINISHING);
    }
}
