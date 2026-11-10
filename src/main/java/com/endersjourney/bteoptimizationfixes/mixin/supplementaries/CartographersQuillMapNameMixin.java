package com.endersjourney.bteoptimizationfixes.mixin.supplementaries;

import com.endersjourney.bteoptimizationfixes.compat.supplementaries.QuillMapName;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ver QuillMapName. CartographersQuillItem#getMapName(CompoundTag) lee la etiqueta equivocada ("decoration"): se anula
 * (devuelve null, asi AdventurerMapsHandler.createStructureMap no pone nombre) y, al terminar createMap, se pone el
 * nombre de la etiqueta "name". Verificado por desensamblado de supplementaries-1.19.2-2.4.20.jar.
 */
@Pseudo
@Mixin(targets = "net.mehvahdjukaar.supplementaries.integration.forge.quark.CartographersQuillItem", remap = false)
public abstract class CartographersQuillMapNameMixin {

    @Inject(method = "getMapName", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$noDecorationAsName(CompoundTag tag, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(null);
    }

    @Inject(method = "createMap", at = @At("RETURN"), remap = false)
    private void bteopt$nameFromTag(ServerLevel level, BlockPos pos, ResourceLocation structure, ItemStack quill,
                                    CallbackInfoReturnable<ItemStack> cir) {
        QuillMapName.apply(quill, cir.getReturnValue());
    }
}
