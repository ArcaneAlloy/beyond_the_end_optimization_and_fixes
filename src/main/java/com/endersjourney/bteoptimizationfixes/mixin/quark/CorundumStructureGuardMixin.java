package com.endersjourney.bteoptimizationfixes.mixin.quark;

import com.endersjourney.bteoptimizationfixes.compat.quark.CorundumStructureGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Ver CorundumStructureGuard. Quark 3.4: UndergroundStyleGenerator.Context#consume(BlockPos, double) se llama para
 * cada bloque de la esfera de la cueva de cristales y decide si es suelo, techo, pared o interior. Si el bloque esta
 * dentro (o al lado) de una pieza de estructura, se ignora. Afecta a todos los estilos subterraneos de Quark (corundum
 * y permafrost). Verificado por desensamblado de Quark-3.4-418.jar.
 */
@Pseudo
@Mixin(targets = "vazkii.quark.content.world.undergroundstyle.base.UndergroundStyleGenerator$Context", remap = false)
public abstract class CorundumStructureGuardMixin {

    @Shadow(remap = false)
    @Final
    public WorldGenRegion world;

    @Unique
    private long bteopt$chunk = Long.MIN_VALUE;

    @Unique
    private List<BoundingBox> bteopt$boxes;

    @Inject(method = "consume", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$skipInsideStructures(BlockPos pos, double noise, CallbackInfo ci) {
        int cx = SectionPos.blockToSectionCoord(pos.getX()), cz = SectionPos.blockToSectionCoord(pos.getZ());
        long key = ChunkPos.asLong(cx, cz);
        if (bteopt$boxes == null || bteopt$chunk != key) {
            bteopt$boxes = CorundumStructureGuard.boxesFor(world, cx, cz);
            bteopt$chunk = key;
        }
        if (!bteopt$boxes.isEmpty() && CorundumStructureGuard.inside(bteopt$boxes, pos)) ci.cancel();
    }
}
