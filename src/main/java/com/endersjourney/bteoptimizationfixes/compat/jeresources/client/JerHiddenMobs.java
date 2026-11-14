package com.endersjourney.bteoptimizationfixes.compat.jeresources.client;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Oculta en JEI las páginas "Mob Drops" de Just Enough Resources de mobs que no se pueden conseguir en el modpack.
 * JER lista todos los mobs registrados con su loot y no tiene opción para esconder ninguno.
 * <ul>
 *   <li>occultism:possessed_skeleton: su ritual de invocación se quitó (KubeJS), pero seguía saliendo como fuente de
 *   la calavera de esqueleto.</li>
 * </ul>
 * Para esconder otro, añadir su id a HIDDEN.
 */
@JeiPlugin
public class JerHiddenMobs implements IModPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("BTEOptimizationFixes/JerHiddenMobs");
    private static final ResourceLocation UID = new ResourceLocation(BTEOptimizationFixes.MODID, "jer_hidden_mobs");
    /** Categoría "Mob Drops" de JER (MobCategory.MOB_TYPE). */
    private static final ResourceLocation JER_MOB = new ResourceLocation("jeresources", "mob");

    private static final Set<String> HIDDEN = Set.of(
            "occultism:possessed_skeleton"
    );

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        try {
            IRecipeManager recipes = jeiRuntime.getRecipeManager();
            Optional<RecipeType<?>> type = recipes.getRecipeType(JER_MOB);
            if (type.isEmpty()) return;   // sin JER
            RecipeType raw = type.get();
            List<Object> hide = ((java.util.stream.Stream<Object>) recipes.createRecipeLookup(raw).get())
                    .filter(JerHiddenMobs::isHidden)
                    .toList();
            if (!hide.isEmpty()) {
                recipes.hideRecipes(raw, hide);
                LOGGER.info("Hidden {} JER mob page(s)", hide.size());
            }
        } catch (Throwable t) {
            LOGGER.warn("Could not hide JER mob pages: {}", t.toString());
        }
    }

    /** MobWrapper.mob (privado) → MobEntry.getEntity() → id del tipo de entidad. */
    private static boolean isHidden(Object wrapper) {
        try {
            Field mobField = wrapper.getClass().getDeclaredField("mob");
            mobField.setAccessible(true);
            Object entry = mobField.get(wrapper);
            if (entry == null) return false;
            Method getEntity = entry.getClass().getMethod("getEntity");
            Object entity = getEntity.invoke(entry);
            if (!(entity instanceof LivingEntity living)) return false;
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(living.getType());
            return id != null && HIDDEN.contains(id.toString());
        } catch (Throwable t) {
            return false;
        }
    }
}
