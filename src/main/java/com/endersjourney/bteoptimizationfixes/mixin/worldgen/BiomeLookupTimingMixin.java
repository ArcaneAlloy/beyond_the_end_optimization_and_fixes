package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.debug.GenTiming;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Herramienta de medición (ver GenTiming): consultas de bioma hechas mientras se aplican las reglas de superficie.
 * Las condiciones "biome" de las reglas preguntan el bioma de cada bloque que evalúan. Fuera de las reglas de
 * superficie no se cuenta nada. Se cuentan todas y se cronometra 1 de cada 16.
 * m_204214_ = BiomeManager.getBiome(BlockPos). Nombre SRG sacado de las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(value = BiomeManager.class, remap = false)
public abstract class BiomeLookupTimingMixin {

    @Inject(method = "m_204214_", at = @At("HEAD"), remap = false)
    private void bteopt$start(CallbackInfoReturnable<Holder<Biome>> cir) {
        GenTiming.biomeStart();
    }

    @Inject(method = "m_204214_", at = @At("RETURN"), remap = false)
    private void bteopt$end(CallbackInfoReturnable<Holder<Biome>> cir) {
        GenTiming.biomeEnd();
    }
}
