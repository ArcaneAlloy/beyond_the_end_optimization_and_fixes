package com.endersjourney.bteoptimizationfixes.mixin.client;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Global Data & Resourcepacks 1.14.5 (WorldGenPackFinderMixin#constructorInject) recarga TODOS los datapacks en el
 * primer init() de la pantalla de crear mundo (~3 s), con una lista que no incluye los datapacks de los mods. Justo
 * despues, el propio openFresh vuelve a aplicar la lista completa (vanilla + mods + globales, en el mismo orden), que
 * es la que se queda: la primera carga se tira entera.
 *
 * Su handler solo recarga si su campo wasInitialized es false. Aqui se pone a true antes del primer init(), asi que
 * esa carga no se hace; la de openFresh sigue igual. Los packs globales siguen disponibles porque Global Packs los
 * anade tambien al repositorio de openFresh (addDefaultPackRepo) y al de la seleccion (addClientPackFinder).
 * Si el campo no existe (otra version de Global Packs) no se toca nada.
 */
@Mixin(value = CreateWorldScreen.class, remap = false)
public abstract class CreateWorldGlobalPacksSkipMixin {

    @Inject(method = "m_7856_()V", at = @At("HEAD"), remap = false, require = 0)
    private void bteopt$skipGlobalPacksReload(CallbackInfo ci) {
        try {
            for (Field f : CreateWorldScreen.class.getDeclaredFields()) {
                if (f.getType() == boolean.class && f.getName().contains("wasInitialized")) {
                    f.setAccessible(true);
                    if (!f.getBoolean(this)) {
                        f.setBoolean(this, true);
                        BTEOptimizationFixes.LOGGER.info("[{}] Crear mundo: se omite la recarga de datapacks de Global Packs (la aplica openFresh)",
                                BTEOptimizationFixes.MODID);
                    }
                    return;
                }
            }
        } catch (Throwable t) {
            BTEOptimizationFixes.LOGGER.warn("[{}] No se pudo omitir la recarga de Global Packs", BTEOptimizationFixes.MODID, t);
        }
    }
}
