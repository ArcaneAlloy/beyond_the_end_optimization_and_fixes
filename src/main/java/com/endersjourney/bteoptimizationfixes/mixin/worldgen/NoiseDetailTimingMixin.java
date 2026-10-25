package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.debug.GenTiming;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * Herramienta de medición (ver GenTiming): un trabajo del generador de ruido que se usa fuera del relleno.
 * m_224239_ = NoiseBasedChunkGenerator.iterateNoiseColumn(...): calcula la columna de ruido entera de un punto, sin la
 * caché del chunk. Lo usan getBaseHeight/getBaseColumn, sobre todo las estructuras para saber a qué altura ponerse.
 * (Crear el NoiseChunk se mide en NoiseChunkReuseMixin, que es donde se reutiliza.)
 * Nombres SRG sacados de las tablas de mapeo de Forge 1.19.2 (official -> SRG).
 */
@Mixin(value = NoiseBasedChunkGenerator.class, remap = false)
public abstract class NoiseDetailTimingMixin {

    @Inject(method = "m_224239_", at = @At("HEAD"), remap = false)
    private void bteopt$columnStart(LevelHeightAccessor height, RandomState randomState, int x, int z,
                                    MutableObject<NoiseColumn> column, Predicate<BlockState> stop,
                                    CallbackInfoReturnable<OptionalInt> cir) {
        GenTiming.noiseColumnAt(x, z);
        GenTiming.detailStart(GenTiming.NOISE_COLUMN);
    }

    @Inject(method = "m_224239_", at = @At("RETURN"), remap = false)
    private void bteopt$columnEnd(CallbackInfoReturnable<OptionalInt> cir) {
        GenTiming.end();
    }
}
