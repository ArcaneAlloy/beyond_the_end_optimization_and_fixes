package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.debug.GenTiming;
import com.endersjourney.bteoptimizationfixes.worldgen.WorldgenOpt;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Herramienta de medición (ver GenTiming): relleno del terreno a partir del ruido.
 * m_224284_ = NoiseBasedChunkGenerator.doFill(...), la parte pesada de la fase noise (se ejecuta en otra tarea, por
 * eso la fila de la fase noise no la incluye). Nombre SRG verificado: es el método que sobrescribe Noisium, cuyos
 * mixins se aplicaron en vuestro modpack.
 */
@Mixin(value = NoiseBasedChunkGenerator.class, remap = false)
public abstract class NoiseFillTimingMixin {

    @Inject(method = "m_224284_", at = @At("HEAD"), remap = false)
    private void bteopt$start(CallbackInfoReturnable<ChunkAccess> cir) {
        GenTiming.otherStart(GenTiming.NOISE_FILL);
    }

    @Inject(method = "m_224284_", at = @At("RETURN"), remap = false)
    private void bteopt$end(CallbackInfoReturnable<ChunkAccess> cir) {
        GenTiming.end();
        WorldgenOpt.recordNoiseHash(cir.getReturnValue());
    }
}
