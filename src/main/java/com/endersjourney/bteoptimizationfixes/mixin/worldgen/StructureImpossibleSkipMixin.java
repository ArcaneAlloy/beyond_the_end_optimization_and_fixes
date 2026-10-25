package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.worldgen.WorldgenOpt;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optimización exacta (ver WorldgenOpt, punto 1): no intentar generar estructuras cuyos biomas no puede dar nunca el
 * generador de biomas de esta dimensión. Ese intento siempre acaba en "no se genera" (Structure.generate exige que el
 * bioma del punto elegido esté en los biomas de la estructura), así que se devuelve directamente.
 * En modo verificación no se salta nada: se deja hacer a vanilla y se comprueba que de verdad no se generó.
 * m_223104_ = ChunkGenerator.tryGenerateStructure(...). Nombre SRG sacado de las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(value = ChunkGenerator.class, remap = false)
public abstract class StructureImpossibleSkipMixin {


    @Inject(method = "m_223104_", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$skipImpossible(StructureSet.StructureSelectionEntry entry, StructureManager structureManager,
                                       RegistryAccess registryAccess, RandomState randomState,
                                       StructureTemplateManager templateManager, long seed, ChunkAccess chunk,
                                       ChunkPos chunkPos, SectionPos sectionPos, CallbackInfoReturnable<Boolean> cir) {
        WorldgenOpt.VERIFYING_STRUCTURE.remove();
        if (!WorldgenOpt.enabled) return;
        Structure structure = entry.structure().value();
        if (WorldgenOpt.canEverSpawn(((ChunkGenerator) (Object) this).getBiomeSource(), structure)) return;
        if (WorldgenOpt.verify) {
            WorldgenOpt.VERIFYING_STRUCTURE.set(structure);
            return;
        }
        WorldgenOpt.STRUCTURES_SKIPPED.increment();
        cir.setReturnValue(false);
    }

    @Inject(method = "m_223104_", at = @At("RETURN"), remap = false)
    private void bteopt$checkSkipped(StructureSet.StructureSelectionEntry entry, StructureManager structureManager,
                                     RegistryAccess registryAccess, RandomState randomState,
                                     StructureTemplateManager templateManager, long seed, ChunkAccess chunk,
                                     ChunkPos chunkPos, SectionPos sectionPos, CallbackInfoReturnable<Boolean> cir) {
        Structure structure = WorldgenOpt.VERIFYING_STRUCTURE.get();
        if (structure == null) return;
        WorldgenOpt.VERIFYING_STRUCTURE.remove();
        WorldgenOpt.STRUCTURES_VERIFIED.increment();
        if (cir.getReturnValueZ()) {
            WorldgenOpt.STRUCTURES_MISMATCH.increment();
            WorldgenOpt.mismatch("la estructura " + registryAccess.registryOrThrow(Registry.STRUCTURE_REGISTRY).getKey(structure)
                    + " sí se generó en el chunk " + chunkPos + " aunque sus biomas no deberían existir en esta dimensión");
        }
    }
}
