package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.worldgen.PureValueCaches;
import com.endersjourney.bteoptimizationfixes.worldgen.WorldgenOpt;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

/**
 * Optimización exacta (ver WorldgenOpt, punto 5): ServerLevel.getUncachedNoiseBiome calcula el bioma de una posición
 * desde cero con el muestreador de clima (todas las funciones de ruido de Tectonic/Terralith) cuando el chunk no está
 * cargado. Lo usan, por ejemplo, Quark (para decidir dónde poner sus estilos subterráneos, mirando chunks vecinos) y
 * Dynamic Trees, y piden las mismas posiciones desde muchos chunks. El bioma de una posición solo depende de la
 * semilla, así que se guarda por posición.
 * m_203675_ = getUncachedNoiseBiome(int, int, int). Nombre SRG sacado de las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(value = ServerLevel.class, remap = false)
public abstract class UncachedBiomeCacheMixin {

    @Unique
    private final PureValueCaches.UncachedBiomes bteopt$uncachedBiomes = new PureValueCaches.UncachedBiomes();

    @Inject(method = "m_203675_", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$cached(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        if (!WorldgenOpt.enabled) return;
        Holder<Biome> cached = this.bteopt$uncachedBiomes.get(x, y, z);
        if (cached == null) return;
        if (WorldgenOpt.verify) {
            WorldgenOpt.EXPECTED_BIOME.set(cached);
            return;
        }
        WorldgenOpt.UNCACHED_BIOMES_HIT.increment();
        cir.setReturnValue(cached);
    }

    @Inject(method = "m_203675_", at = @At("RETURN"), remap = false)
    private void bteopt$store(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        if (!WorldgenOpt.enabled) return;
        Holder<Biome> actual = cir.getReturnValue();
        Holder<Biome> expected = WorldgenOpt.EXPECTED_BIOME.get();
        if (expected != null) {
            WorldgenOpt.EXPECTED_BIOME.remove();
            WorldgenOpt.UNCACHED_BIOMES_VERIFIED.increment();
            if (expected != actual && !Objects.equals(expected, actual)) {
                WorldgenOpt.UNCACHED_BIOMES_MISMATCH.increment();
                WorldgenOpt.mismatch("bioma sin chunk distinto en " + x + ", " + y + ", " + z);
            }
            return;
        }
        if (actual != null) this.bteopt$uncachedBiomes.put(x, y, z, actual);
    }
}
