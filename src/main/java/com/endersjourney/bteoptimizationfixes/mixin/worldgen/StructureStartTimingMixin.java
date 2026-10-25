package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.debug.GenTiming;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Herramienta de medición (ver GenTiming): tiempo de colocar cada estructura en un chunk.
 * m_226850_ = StructureStart.placeInChunk(...). Nombre SRG sacado de los volcados de hilos del modpack
 * (ChunkGenerator.applyBiomeDecoration -> StructureStart.m_226850_).
 */
@Mixin(value = StructureStart.class, remap = false)
public abstract class StructureStartTimingMixin {

    @Inject(method = "m_226850_", at = @At("HEAD"), remap = false)
    private void bteopt$start(CallbackInfo ci) {
        if (GenTiming.enabled) GenTiming.structureStart(((StructureStart) (Object) this).getStructure());
    }

    @Inject(method = "m_226850_", at = @At("RETURN"), remap = false)
    private void bteopt$end(CallbackInfo ci) {
        GenTiming.end();
    }
}
