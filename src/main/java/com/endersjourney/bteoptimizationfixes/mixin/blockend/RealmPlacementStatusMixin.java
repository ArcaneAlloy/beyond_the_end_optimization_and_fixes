package com.endersjourney.bteoptimizationfixes.mixin.blockend;

import com.endersjourney.bteoptimizationfixes.loading.WorldLoadingStatus;
import com.endersjourney.bteoptimizationfixes.loading.WorldLoadingStatus.Phase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BlockEnd ("Don't Spawn the Ender Dragon", 1.3): StructureManager$Structure
 * #place() genera la isla de The End. Solo se llama desde verify() mientras
 * no esta colocada, una vez por mundo, en el primer tick del servidor.
 * Solo marca la fase para la pantalla de carga.
 *
 * Verificado por desensamblado del jar real
 * (dont_spawn_ender_dragon_with_tower_1.19.2-1.19.4-1.3.jar).
 */
@Pseudo
@Mixin(targets = "fr.shoqapik.blockend.structures.StructureManager$Structure", remap = false)
public abstract class RealmPlacementStatusMixin {

    @Inject(method = "place()V", at = @At("HEAD"), remap = false)
    private void bteopt$placeStart(CallbackInfo ci) {
        WorldLoadingStatus.set(Phase.BLOCKEND_REALM);
    }

    @Inject(method = "place()V", at = @At("RETURN"), remap = false)
    private void bteopt$placeEnd(CallbackInfo ci) {
        WorldLoadingStatus.set(Phase.FINISHING);
    }
}
