package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.debug.GenTiming;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Herramienta de medición (ver GenTiming): elección de bioma de cada sección del chunk.
 * m_188003_ = LevelChunkSection.fillBiomesFromNoise(...), la parte pesada de la fase biomes (se ejecuta en otra
 * tarea). Nombre SRG verificado: también lo sobrescribe Noisium, cuyos mixins se aplicaron en vuestro modpack.
 */
@Mixin(value = LevelChunkSection.class, remap = false)
public abstract class BiomeFillTimingMixin {

    @Inject(method = "m_188003_", at = @At("HEAD"), remap = false)
    private void bteopt$start(CallbackInfo ci) {
        GenTiming.otherStart(GenTiming.BIOME_FILL);
    }

    @Inject(method = "m_188003_", at = @At("RETURN"), remap = false)
    private void bteopt$end(CallbackInfo ci) {
        GenTiming.end();
    }
}
