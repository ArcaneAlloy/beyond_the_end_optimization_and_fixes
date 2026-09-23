package com.endersjourney.bteoptimizationfixes.mixin.kubejs;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Consumer;

/**
 * KubeJSPlugins.forEachPlugin(Consumer) es literalmente "LIST.forEach(callback)",
 * sin try/catch alrededor de cada plugin. Esto importa mucho porque
 * KubeJS.loadComplete() (enganchado a FMLLoadCompleteEvent, durante la
 * carga de mods) llama a forEachPlugin(KubeJSPlugin::afterInit) como su
 * PRIMER paso -- y solo si ese paso completa sin excepciones llega a la
 * ultima linea del metodo, que es RecipeNamespace.getAll() (el
 * calentamiento de recetas que investigamos hoy).
 *
 * Si CUALQUIER plugin de KubeJS de CUALQUIER mod del pack (326 mods,
 * muchos con su propia integracion KubeJS) revienta en afterInit(), toda
 * la secuencia se aborta ahi -- incluyendo el calentamiento de
 * RecipeNamespace, que entonces se paga mas tarde, la primera vez que
 * algo mas lo necesita (la pantalla de crear mundo, ~14s que vimos con
 * JFR).
 *
 * Este Mixin sustituye el forEach por una version que atrapa y registra
 * la excepcion de un plugin individual, y sigue con el resto -- en vez de
 * competir con el calentamiento propio de KubeJS (como el intento
 * anterior, que choco en carrera con el), esto deja que el calentamiento
 * YA EXISTENTE de KubeJS llegue a ejecutarse de verdad. Efecto secundario
 * bueno: tambien protege ScriptsLoadedEvent, POST_INIT y
 * postModificationEvents (las otras tres cosas que loadComplete() hace
 * antes del calentamiento), asi que si el problema es real esto ayuda a
 * mas que solo esta optimizacion.
 *
 * AVISO: no se ha podido compilar contra las clases reales de KubeJS en
 * este Mixin (a diferencia de KubeJSRecipeNamespaceWarmup, que si estaba
 * en el classpath de compilacion) -- @Pseudo hace que cargue igual si la
 * firma no coincide exactamente en una futura version. Probadlo antes de
 * distribuirlo, y mirad el log para ver si aparece el mensaje de error
 * nuevo (confirmaria la hipotesis) o no aparece nunca (indicaria que el
 * problema esta en otro sitio).
 */
@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.util.KubeJSPlugins", remap = false)
public abstract class KubeJSPluginsMixin {

    @Redirect(
            method = "forEachPlugin",
            require = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V",
                    remap = false
            )
    )
    private static void bteopt$forEachPluginSafely(List<Object> list, Consumer<Object> callback) {
        for (Object plugin : list) {
            try {
                callback.accept(plugin);
            } catch (Throwable t) {
                BTEOptimizationFixes.LOGGER.error(
                        "[bteopt] Un plugin de KubeJS fallo durante forEachPlugin ({}), se continua con el resto. " +
                        "Esto es justo lo que sospechabamos que bloqueaba el calentamiento de RecipeNamespace.",
                        plugin, t
                );
            }
        }
    }
}
