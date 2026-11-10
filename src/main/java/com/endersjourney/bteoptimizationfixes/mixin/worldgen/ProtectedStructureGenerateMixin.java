package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.compat.protectyourstructures.ProtectedStructureSpacing;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ver ProtectedStructureSpacing: al generar, una estructura no protegida que se pegaria a una protegida no se coloca
 * (tryGenerateStructure devuelve false, como cuando no cabe en el bioma).
 *
 * m_223104_ = ChunkGenerator.tryGenerateStructure(...), m_73603_ = StructureStart.isValid(). Comprobado en
 * server-1.19.2-20220805.130853-srg.jar con las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(value = ChunkGenerator.class, remap = false)
public abstract class ProtectedStructureGenerateMixin {

    @Inject(method = "m_223104_", at = @At("HEAD"), remap = false)
    private void bteopt$rememberStructure(StructureSet.StructureSelectionEntry entry, StructureManager structureManager,
                                          RegistryAccess registryAccess, RandomState randomState,
                                          StructureTemplateManager templateManager, long seed, ChunkAccess chunk,
                                          ChunkPos chunkPos, SectionPos sectionPos, CallbackInfoReturnable<Boolean> cir) {
        ProtectedStructureSpacing.TRY_GENERATE.set(new ProtectedStructureSpacing.Pending(entry.structure().value(),
                new ProtectedStructureSpacing.Context(registryAccess, (ChunkGenerator) (Object) this, randomState,
                        templateManager, chunk, seed)));
    }

    @Redirect(
            method = "m_223104_",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/structure/StructureStart;m_73603_()Z",
                    remap = false),
            remap = false
    )
    private boolean bteopt$skipNextToProtected(StructureStart start) {
        if (!start.isValid()) return false;
        ProtectedStructureSpacing.Pending pending = ProtectedStructureSpacing.TRY_GENERATE.get();
        ProtectedStructureSpacing.TRY_GENERATE.remove();
        if (pending == null) return true;
        return !ProtectedStructureSpacing.isBlocked(pending.structure(), start.getChunkPos(), start::getBoundingBox,
                pending.context());
    }
}
