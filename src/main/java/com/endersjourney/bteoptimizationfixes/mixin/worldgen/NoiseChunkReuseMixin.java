package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.debug.GenTiming;
import com.endersjourney.bteoptimizationfixes.worldgen.NoiseChunkCache;
import com.endersjourney.bteoptimizationfixes.worldgen.WorldgenOpt;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optimización exacta (ver WorldgenOpt, punto 3, y NoiseChunkCache): si el chunk llega sin NoiseChunk porque se
 * descargó y se volvió a cargar, se le devuelve el que se creó para él antes. Además mide (GenTiming) el tiempo de
 * crear un NoiseChunk nuevo; los reutilizados no se miden porque no cuestan.
 * m_224256_ = NoiseBasedChunkGenerator.createNoiseChunk(ChunkAccess, StructureManager, Blender, RandomState).
 * Solo lo llama ChunkAccess.getOrCreateNoiseChunk cuando el chunk no tiene NoiseChunk. Nombre SRG sacado de las tablas
 * de mapeo de Forge 1.19.2.
 */
@Mixin(value = NoiseBasedChunkGenerator.class, remap = false)
public abstract class NoiseChunkReuseMixin {

    @Inject(method = "m_224256_", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$reuse(ChunkAccess chunk, StructureManager structureManager, Blender blender,
                              RandomState randomState, CallbackInfoReturnable<NoiseChunk> cir) {
        if (WorldgenOpt.enabled && blender == Blender.empty()) {
            NoiseChunk cached = NoiseChunkCache.get(randomState, chunk.getPos());
            if (cached != null) {
                WorldgenOpt.NOISECHUNKS_REUSED.increment();
                cir.setReturnValue(cached);
                return;
            }
        }
        GenTiming.detailStart(GenTiming.CREATE_NOISE_CHUNK);
    }

    @Inject(method = "m_224256_", at = @At("RETURN"), remap = false)
    private void bteopt$store(ChunkAccess chunk, StructureManager structureManager, Blender blender,
                              RandomState randomState, CallbackInfoReturnable<NoiseChunk> cir) {
        GenTiming.end();
        WorldgenOpt.NOISECHUNKS_CREATED.increment();
        if (WorldgenOpt.enabled && blender == Blender.empty() && cir.getReturnValue() != null) {
            NoiseChunkCache.put(randomState, chunk.getPos(), cir.getReturnValue());
        }
    }
}
