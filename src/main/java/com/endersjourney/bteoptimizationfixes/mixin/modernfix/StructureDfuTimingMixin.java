package com.endersjourney.bteoptimizationfixes.mixin.modernfix;

import com.endersjourney.bteoptimizationfixes.debug.StructureLoadTiming;
import com.mojang.datafixers.DataFixer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;

/**
 * Herramienta de medición: cronometra cada carga de estructura NBT. Ver StructureLoadTiming.
 *
 * Con ModernFix (mixin.perf.cache_upgraded_structures, activo por defecto) todas las estructuras que se cargan desde
 * mods y datapacks pasan por CachingStructureManager.readStructureTag(ResourceLocation, DataFixer, InputStream).
 * Verificado por descompilación de modernfix-forge-5.18.1+mc1.19.2.jar:
 *   - lee el archivo con NbtIo.readCompressed(InputStream)   -> aquí se apunta la DataVersion de origen
 *   - si es antigua y no está en la caché, llama a NbtUtils.update(DataFixer, DataFixTypes, CompoundTag, int, int)
 *                                                             -> aquí se cronometra la conversión
 * Las llamadas de dentro de la clase de ModernFix ya usan nombres SRG (remap = false), por eso los targets van en SRG:
 * m_128939_ = NbtIo.readCompressed(InputStream), m_129218_ = NbtUtils.update(..., int, int).
 */
@Pseudo
@Mixin(targets = "org.embeddedt.modernfix.structure.CachingStructureManager", remap = false)
public abstract class StructureDfuTimingMixin {

    @Inject(method = "readStructureTag", at = @At("HEAD"), remap = false)
    private static void bteopt$start(ResourceLocation location, DataFixer datafixer, InputStream stream,
                                     CallbackInfoReturnable<CompoundTag> cir) {
        StructureLoadTiming.start(location);
    }

    @Redirect(method = "readStructureTag",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtIo;m_128939_(Ljava/io/InputStream;)Lnet/minecraft/nbt/CompoundTag;"),
            remap = false)
    private static CompoundTag bteopt$readVersion(InputStream stream) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(stream);
        StructureLoadTiming.dataVersion(tag.contains("DataVersion", 99) ? tag.getInt("DataVersion") : 500);
        return tag;
    }

    @Redirect(method = "readStructureTag",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtUtils;m_129218_(Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/util/datafix/DataFixTypes;Lnet/minecraft/nbt/CompoundTag;II)Lnet/minecraft/nbt/CompoundTag;"),
            remap = false)
    private static CompoundTag bteopt$timeDfu(DataFixer fixer, DataFixTypes type, CompoundTag tag, int from, int to) {
        boolean first = StructureLoadTiming.beginDfu();
        long start = System.nanoTime();
        CompoundTag result = NbtUtils.update(fixer, type, tag, from, to);
        StructureLoadTiming.dfu(System.nanoTime() - start, first);
        return result;
    }

    @Inject(method = "readStructureTag", at = @At("RETURN"), remap = false)
    private static void bteopt$end(ResourceLocation location, DataFixer datafixer, InputStream stream,
                                   CallbackInfoReturnable<CompoundTag> cir) {
        StructureLoadTiming.end();
    }
}
