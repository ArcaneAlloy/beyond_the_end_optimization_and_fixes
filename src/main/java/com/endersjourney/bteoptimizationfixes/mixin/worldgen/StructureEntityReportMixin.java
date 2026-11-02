package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.worldgen.DiscardedReports;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * StructureTemplate#createEntityIgnoreException (m_74543_) crea las entidades guardadas en una plantilla y, si fallan,
 * ignora la excepcion (devuelve Optional.empty()). Aqui solo se marca el hilo mientras dura, para que el CrashReport
 * que se construye y se tira por el camino no consulte el hardware (SystemReportHardwareSkipMixin).
 * El resultado (entidad creada o no) no cambia.
 */
@Mixin(value = StructureTemplate.class, remap = false)
public abstract class StructureEntityReportMixin {

    @Inject(method = "m_74543_(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Optional;",
            at = @At("HEAD"), remap = false, require = 0)
    private static void bteopt$enter(ServerLevelAccessor level, CompoundTag tag, CallbackInfoReturnable<Optional<Entity>> cir) {
        DiscardedReports.enter();
    }

    @Inject(method = "m_74543_(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Optional;",
            at = @At("RETURN"), remap = false, require = 0)
    private static void bteopt$exit(ServerLevelAccessor level, CompoundTag tag, CallbackInfoReturnable<Optional<Entity>> cir) {
        DiscardedReports.exit();
    }
}
