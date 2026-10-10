package com.endersjourney.bteoptimizationfixes.mixin.beyondtheend;

import com.endersjourney.bteoptimizationfixes.loading.WorldLoadingStatus;
import com.endersjourney.bteoptimizationfixes.loading.WorldLoadingStatus.Phase;
import net.minecraftforge.event.server.ServerStartingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Beyond The End 1.8.0: StructureWarmup#onServerStarting precarga las 14
 * estructuras del lobby (~12 s en el log de referencia), ya con la barra
 * de carga al 100%. Solo marca la fase para la pantalla de carga; no
 * cambia nada de lo que hace el metodo.
 *
 * Verificado por desensamblado del jar real (beyondtheend-1.8.0.jar).
 */
@Pseudo
@Mixin(targets = "com.TBK.beyondtheend.server.StructureWarmup", remap = false)
public abstract class LobbyWarmupStatusMixin {

    @Inject(method = "onServerStarting", at = @At("HEAD"), remap = false)
    private static void bteopt$warmupStart(ServerStartingEvent event, CallbackInfo ci) {
        WorldLoadingStatus.set(Phase.LOBBY_STRUCTURES);
    }

    @Inject(method = "onServerStarting", at = @At("RETURN"), remap = false)
    private static void bteopt$warmupEnd(ServerStartingEvent event, CallbackInfo ci) {
        WorldLoadingStatus.set(Phase.PREPARING);
    }
}
