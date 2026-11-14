package com.endersjourney.bteoptimizationfixes.mixin.jei;

import com.endersjourney.bteoptimizationfixes.compat.jei.RecipeSlotTooltipIngredient;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * JEI 1.19.2-11.8.1: los tooltips de las casillas de receta se pintaban sin el objeto. RecipeLayout.drawOverlays crea
 * un JeiTooltip, le mete las líneas de la casilla y lo pinta sin setIngredient, así que Forge lanza
 * RenderTooltipEvent.GatherComponents con ItemStack.EMPTY: los mods que añaden componentes gráficos al tooltip (la
 * línea de atributos con iconos de Quark, previsualizaciones...) no salían en ninguna categoría de JEI, aunque sí en
 * su lista de objetos, que sí pasa el objeto.
 *
 * Aquí se apunta el objeto de la casilla cuando JEI pide sus líneas; JeiTooltipIngredientMixin se lo pone al tooltip
 * justo al pintarlo.
 *
 * Verificado por desensamblado de jei-1.19.2-forge-11.8.1.1034.jar.
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false)
public abstract class JeiRecipeLayoutTooltipMixin {

    @Redirect(method = "drawOverlays",
            at = @At(value = "INVOKE",
                    target = "Lmezz/jei/api/gui/ingredient/IRecipeSlotDrawable;getTooltip()Ljava/util/List;"),
            remap = false)
    private List<Component> bteopt$rememberIngredient(IRecipeSlotDrawable slot) {
        RecipeSlotTooltipIngredient.set(slot.getDisplayedIngredient().orElse(null));
        return slot.getTooltip();
    }
}
