package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.debug.GenTiming;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Herramienta de medición (ver GenTiming): coste de decidir si una estructura empieza en un chunk (fase
 * structure_starts), por estructura.
 * m_226596_ = Structure.generate(...): busca el punto de generación (alturas del terreno, piezas del jigsaw...) y
 * comprueba el bioma. m_226589_ = Structure.isValidBiome(...), la comprobación de bioma, que se apunta aparte.
 * Nombres SRG sacados de las tablas de mapeo de Forge 1.19.2 (official -> SRG).
 */
@Mixin(value = Structure.class, remap = false)
public abstract class StructureGenerateTimingMixin {

    @Inject(method = "m_226596_", at = @At("HEAD"), remap = false)
    private void bteopt$start(CallbackInfoReturnable<StructureStart> cir) {
        GenTiming.structureGenStart(this);
    }

    @Inject(method = "m_226596_", at = @At("RETURN"), remap = false)
    private void bteopt$end(CallbackInfoReturnable<StructureStart> cir) {
        GenTiming.end();
    }

    @Inject(method = "m_226589_", at = @At("HEAD"), remap = false)
    private static void bteopt$biomeStart(CallbackInfoReturnable<Boolean> cir) {
        GenTiming.detailStart(GenTiming.STRUCTURE_BIOME_CHECK);
    }

    @Inject(method = "m_226589_", at = @At("RETURN"), remap = false)
    private static void bteopt$biomeEnd(CallbackInfoReturnable<Boolean> cir) {
        GenTiming.end();
    }
}
