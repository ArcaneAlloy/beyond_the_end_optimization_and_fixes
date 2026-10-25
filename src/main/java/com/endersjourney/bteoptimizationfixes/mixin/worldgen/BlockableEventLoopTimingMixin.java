package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.debug.MainThreadTiming;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Herramienta de medición (ver MainThreadTiming): tiempo del hilo principal esperando y ejecutando tareas.
 * m_5667_ = BlockableEventLoop.waitForTasks(), m_6367_ = BlockableEventLoop.doRunTask(R). Nombres SRG sacados de
 * los volcados de hilos del modpack (el hilo principal aparece aparcado en m_5667_ y ejecutando tareas de chunks en
 * ServerChunkCache$MainThreadExecutor.m_6367_ -> BlockableEventLoop.m_6367_). Solo cuenta en el "Server thread".
 */
@Mixin(value = BlockableEventLoop.class, remap = false)
public abstract class BlockableEventLoopTimingMixin {

    @Inject(method = "m_5667_", at = @At("HEAD"), remap = false)
    private void bteopt$waitStart(CallbackInfo ci) {
        MainThreadTiming.waitStart();
    }

    @Inject(method = "m_5667_", at = @At("RETURN"), remap = false)
    private void bteopt$waitEnd(CallbackInfo ci) {
        MainThreadTiming.waitEnd();
    }

    @Inject(method = "m_6367_", at = @At("HEAD"), remap = false)
    private void bteopt$taskStart(Runnable task, CallbackInfo ci) {
        MainThreadTiming.taskStart(task);
    }

    @Inject(method = "m_6367_", at = @At("RETURN"), remap = false)
    private void bteopt$taskEnd(Runnable task, CallbackInfo ci) {
        MainThreadTiming.taskEnd();
    }
}
