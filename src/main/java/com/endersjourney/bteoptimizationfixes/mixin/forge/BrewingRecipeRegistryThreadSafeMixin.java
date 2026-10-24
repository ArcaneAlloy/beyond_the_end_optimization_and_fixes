package com.endersjourney.bteoptimizationfixes.mixin.forge;

import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Cierre aleatorio al arrancar: "Mutant More (mutantmore) encountered an error during the common_setup event phase
 * java.lang.ArrayIndexOutOfBoundsException: Index 16 out of bounds for length 15".
 *
 * FMLCommonSetupEvent se reparte entre varios hilos a la vez. Mutant More (y otros mods) llaman a
 * BrewingRecipeRegistry.addRecipe directamente en ese evento, sin event.enqueueWork, y la lista de recetas de Forge es
 * un ArrayList normal: si dos mods añaden una receta a la vez, el ArrayList se rompe al crecer y lanza la excepción.
 * Depende de qué hilo llegue antes, por eso solo pasa de vez en cuando.
 *
 * Arreglo: el add de addRecipe(IBrewingRecipe) se hace dentro de un synchronized sobre la propia lista. La otra
 * versión de addRecipe (input, ingrediente, resultado) acaba llamando a esta, así que todas las altas quedan cubiertas.
 */
@Mixin(value = BrewingRecipeRegistry.class, remap = false)
public abstract class BrewingRecipeRegistryThreadSafeMixin {

    @Redirect(method = "addRecipe(Lnet/minecraftforge/common/brewing/IBrewingRecipe;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"), remap = false)
    private static boolean bteopt$synchronizedAdd(List<Object> recipes, Object recipe) {
        synchronized (recipes) {
            return recipes.add(recipe);
        }
    }
}
