package com.endersjourney.bteoptimizationfixes.mixin.jei;

import com.endersjourney.bteoptimizationfixes.compat.jei.RecipeSlotTooltipIngredient;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.ingredients.ITypedIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * JEI 1.19.2-11.8.1: pone al tooltip de la casilla de receta el objeto que apuntó JeiRecipeLayoutTooltipMixin, para
 * que se pinte por el mismo camino que el de la lista de objetos (con el ItemStack y su imagen de tooltip) y los mods
 * puedan añadir sus componentes. Ver JeiRecipeLayoutTooltipMixin.
 *
 * Verificado por desensamblado de jei-1.19.2-forge-11.8.1.1034.jar.
 */
@Pseudo
@Mixin(targets = "mezz.jei.common.gui.JeiTooltip", remap = false)
public abstract class JeiTooltipIngredientMixin {

    @Shadow(remap = false)
    private ITypedIngredient<?> typedIngredient;

    @Inject(method = "draw(Lcom/mojang/blaze3d/vertex/PoseStack;II)V", at = @At("HEAD"), remap = false)
    private void bteopt$useSlotIngredient(PoseStack poseStack, int x, int y, CallbackInfo ci) {
        ITypedIngredient<?> ingredient = RecipeSlotTooltipIngredient.take();
        if (this.typedIngredient == null && ingredient != null) this.typedIngredient = ingredient;
    }
}
