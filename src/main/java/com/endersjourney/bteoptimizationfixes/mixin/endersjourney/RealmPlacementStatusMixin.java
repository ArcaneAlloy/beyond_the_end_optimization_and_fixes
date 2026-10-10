package com.endersjourney.bteoptimizationfixes.mixin.endersjourney;

import com.endersjourney.bteoptimizationfixes.loading.WorldLoadingStatus;
import com.endersjourney.bteoptimizationfixes.loading.WorldLoadingStatus.Phase;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ender's Journey 1.1.12: RealmManager$Structure#verify() hace, en el
 * primer tick, dos trabajos pesados:
 *  - placeColumn(ServerLevel): la columna/plataforma de X:5000 (una vez por
 *    sesion).
 *  - place(): el reino (The Forgotten Realm), solo mientras no esta colocado.
 * Solo marca la fase para la pantalla de carga.
 *
 * Verificado por desensamblado del jar real (enders_journey-1.1.12.jar).
 */
@Pseudo
@Mixin(targets = "mc.duzo.ender_journey.realm.RealmManager$Structure", remap = false)
public abstract class RealmPlacementStatusMixin {

    @Inject(method = "placeColumn(Lnet/minecraft/server/level/ServerLevel;)V", at = @At("HEAD"), remap = false)
    private void bteopt$columnStart(ServerLevel level, CallbackInfo ci) {
        WorldLoadingStatus.set(Phase.ENDERS_JOURNEY_REALM);
    }

    @Inject(method = "placeColumn(Lnet/minecraft/server/level/ServerLevel;)V", at = @At("RETURN"), remap = false)
    private void bteopt$columnEnd(ServerLevel level, CallbackInfo ci) {
        WorldLoadingStatus.set(Phase.FINISHING);
    }

    @Inject(method = "place()V", at = @At("HEAD"), remap = false)
    private void bteopt$placeStart(CallbackInfo ci) {
        WorldLoadingStatus.set(Phase.ENDERS_JOURNEY_REALM);
    }

    @Inject(method = "place()V", at = @At("RETURN"), remap = false)
    private void bteopt$placeEnd(CallbackInfo ci) {
        WorldLoadingStatus.set(Phase.FINISHING);
    }
}
