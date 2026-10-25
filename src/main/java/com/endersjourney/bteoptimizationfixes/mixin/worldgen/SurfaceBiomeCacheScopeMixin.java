package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.worldgen.SurfaceBiomeCache;
import com.endersjourney.bteoptimizationfixes.worldgen.WorldgenOpt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * Optimización exacta (ver WorldgenOpt, punto 2): mientras se aplican las reglas de superficie de un chunk, las reglas
 * reciben una función de biomas que responde desde la caché del chunk (SurfaceBiomeCache.wrap) en lugar de llamar a
 * BiomeManager.getBiome para cada bloque.
 * m_224648_ = SurfaceSystem.buildSurface(...). Nombre SRG sacado de las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(value = SurfaceSystem.class, remap = false)
public abstract class SurfaceBiomeCacheScopeMixin {

    @Inject(method = "m_224648_", at = @At("HEAD"), remap = false)
    private void bteopt$begin(RandomState randomState, BiomeManager biomeManager, Registry<Biome> biomes, boolean legacy,
                              WorldGenerationContext context, ChunkAccess chunk, NoiseChunk noiseChunk,
                              SurfaceRules.RuleSource rule, CallbackInfo ci) {
        if (WorldgenOpt.enabled) SurfaceBiomeCache.begin(biomeManager, chunk);
    }

    /** El quinto argumento de new SurfaceRules.Context(...) es la función de biomas (biomeManager::getBiome). */
    @ModifyArg(method = "m_224648_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;<init>(Lnet/minecraft/world/level/levelgen/SurfaceSystem;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Ljava/util/function/Function;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)V", remap = false), index = 4, remap = false)
    private Function<BlockPos, Holder<Biome>> bteopt$cachedBiomes(Function<BlockPos, Holder<Biome>> vanilla) {
        return WorldgenOpt.enabled ? SurfaceBiomeCache.wrapCurrent(vanilla) : vanilla;
    }

    @Inject(method = "m_224648_", at = @At("RETURN"), remap = false)
    private void bteopt$end(RandomState randomState, BiomeManager biomeManager, Registry<Biome> biomes, boolean legacy,
                            WorldGenerationContext context, ChunkAccess chunk, NoiseChunk noiseChunk,
                            SurfaceRules.RuleSource rule, CallbackInfo ci) {
        SurfaceBiomeCache.end();
    }
}
