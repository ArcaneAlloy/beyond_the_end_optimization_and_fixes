package com.endersjourney.bteoptimizationfixes.compat.jei;

import mezz.jei.api.ingredients.ITypedIngredient;

/**
 * JEI 1.19.2-11.8.1: el objeto de la casilla de receta bajo el ratón, apuntado por JeiRecipeLayoutTooltipMixin justo
 * antes de que JEI pinte su tooltip y recogido por JeiTooltipIngredientMixin. Solo se usa en el hilo de render.
 */
public final class RecipeSlotTooltipIngredient {

    private static ITypedIngredient<?> pending = null;

    private RecipeSlotTooltipIngredient() {}

    public static void set(ITypedIngredient<?> ingredient) {
        pending = ingredient;
    }

    /** Devuelve el objeto apuntado (si lo hay) y lo borra, para que no se use en otro tooltip. */
    public static ITypedIngredient<?> take() {
        ITypedIngredient<?> ingredient = pending;
        pending = null;
        return ingredient;
    }
}
