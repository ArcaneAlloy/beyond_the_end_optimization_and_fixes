package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.debug.GenTiming;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Herramienta de medición (ver GenTiming): tiempo de cada fase del chunk.
 * m_223279_ = ChunkStatus.generate(...). Nombre SRG sacado de los volcados de hilos del propio modpack
 * (ChunkMap.scheduleChunkGeneration -> ChunkStatus.m_223279_). remap = false porque el build no genera refmap con
 * nombres SRG para clases de Minecraft.
 */
@Mixin(value = ChunkStatus.class, remap = false)
public abstract class ChunkStatusTimingMixin {

    @Inject(method = "m_223279_", at = @At("HEAD"), remap = false)
    private void bteopt$start(CallbackInfoReturnable<?> cir) {
        GenTiming.statusStart(this);
    }

    @Inject(method = "m_223279_", at = @At("RETURN"), remap = false)
    private void bteopt$end(CallbackInfoReturnable<?> cir) {
        GenTiming.end();
    }
}
