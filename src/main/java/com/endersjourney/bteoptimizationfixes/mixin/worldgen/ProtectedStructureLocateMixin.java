package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.compat.protectyourstructures.ProtectedStructureSpacing;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Ver ProtectedStructureSpacing: /locate, las brujulas y los mapas de exploracion preguntan a StructureCheck si una
 * estructura saldria en un chunk que aun no existe. Aqui se responde "no" para las que no se van a generar por estar
 * pegadas a una protegida, para que no se senale un sitio vacio.
 *
 * m_226755_ = StructureCheck.canCreateStructure(ChunkPos, Structure). Campos: f_197238_ registryAccess,
 * f_197242_ chunkGenerator, f_226710_ randomState, f_226709_ structureTemplateManager, f_197243_ heightAccessor,
 * f_197245_ seed. Comprobado en server-1.19.2-20220805.130853-srg.jar.
 */
@Mixin(value = StructureCheck.class, remap = false)
public abstract class ProtectedStructureLocateMixin {

    @Shadow(remap = false) @Final private RegistryAccess f_197238_;
    @Shadow(remap = false) @Final private ChunkGenerator f_197242_;
    @Shadow(remap = false) @Final private RandomState f_226710_;
    @Shadow(remap = false) @Final private StructureTemplateManager f_226709_;
    @Shadow(remap = false) @Final private LevelHeightAccessor f_197243_;
    @Shadow(remap = false) @Final private long f_197245_;

    @Inject(method = "m_226755_", at = @At("HEAD"), remap = false)
    private void bteopt$rememberStructure(ChunkPos pos, Structure structure, CallbackInfoReturnable<Boolean> cir) {
        ProtectedStructureSpacing.LOCATE.set(new ProtectedStructureSpacing.Pending(structure,
                new ProtectedStructureSpacing.Context(f_197238_, f_197242_, f_226710_, f_226709_, f_197243_, f_197245_)));
    }

    @Redirect(
            method = "m_226755_",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;isPresent()Z", remap = false),
            remap = false
    )
    private boolean bteopt$skipNextToProtected(Optional<Structure.GenerationStub> stub) {
        if (stub.isEmpty()) return false;
        ProtectedStructureSpacing.Pending pending = ProtectedStructureSpacing.LOCATE.get();
        ProtectedStructureSpacing.LOCATE.remove();
        if (pending == null) return true;
        ChunkPos origin = new ChunkPos(stub.get().position());
        return !ProtectedStructureSpacing.isBlocked(pending.structure(), origin, () -> {
            PiecesContainer pieces = stub.get().getPiecesBuilder().build();
            return pieces.isEmpty() ? null : pieces.calculateBoundingBox();
        }, pending.context());
    }
}
