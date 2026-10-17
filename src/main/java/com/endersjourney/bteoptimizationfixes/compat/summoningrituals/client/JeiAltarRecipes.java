package com.endersjourney.bteoptimizationfixes.compat.summoningrituals.client;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Plugin de JEI: guarda el runtime para poder abrir desde el juego todas las recetas del altar de Summoning Rituals
 * (las de su catalizador, el Altar). Solo se carga si JEI está instalado; AltarRecipesHint no llama aquí sin JEI.
 */
@JeiPlugin
public class JeiAltarRecipes implements IModPlugin {

    private static final ResourceLocation UID = new ResourceLocation(BTEOptimizationFixes.MODID, "altar_recipes");
    private static final ResourceLocation ALTAR = new ResourceLocation("summoningrituals", "altar");

    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** Abre JEI con las recetas en las que el Altar es catalizador (todas las invocaciones). */
    static void show() {
        if (runtime == null) return;
        Item altar = ForgeRegistries.ITEMS.getValue(ALTAR);
        if (altar == null || altar == Items.AIR) return;
        IFocus<ItemStack> focus = runtime.getJeiHelpers().getFocusFactory()
                .createFocus(RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK, new ItemStack(altar));
        runtime.getRecipesGui().show(focus);
    }
}
