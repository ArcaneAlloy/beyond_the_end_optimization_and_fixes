package com.endersjourney.bteoptimizationfixes.mixin.mimic;

import com.endersjourney.bteoptimizationfixes.compat.mimic.MimicTemplateChests;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ver MimicTemplateChests. m_226850_ = StructureStart.placeInChunk(WorldGenLevel, StructureManager, ChunkGenerator,
 * RandomSource, BoundingBox, ChunkPos), f_226844_ = structure, f_163592_ = chunkPos (chunk donde empieza). Comprobado en server-1.19.2-20220805.130853-srg.jar.
 */
@Mixin(value = StructureStart.class, remap = false)
public abstract class MimicTemplateChestsMixin {

    @Shadow(remap = false)
    @Final
    private Structure f_226844_;

    @Shadow(remap = false)
    @Final
    private ChunkPos f_163592_;

    @Inject(method = "m_226850_", at = @At("RETURN"), remap = false)
    private void bteopt$mimicTemplateChests(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                                            RandomSource random, BoundingBox box, ChunkPos chunkPos, CallbackInfo ci) {
        MimicTemplateChests.afterPlaceInChunk(level, this.f_226844_, this.f_163592_, box, chunkPos);
    }
}
