package com.endersjourney.bteoptimizationfixes.mixin.dynamictrees;

import com.ferreusveritas.dynamictrees.api.registry.RegistryEntry;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * JsonRegistryResourceLoader.postLoad(LoadData) llama incondicionalmente a
 * ((RegistryEntry) loadData.resource).toLoadDataString() /
 * .toReloadDataString() como argumento de LOGGER.debug(...):
 *
 *   LOGGER.debug("Loaded type \"{}\" data: {}.", this.registryName,
 *                ((RegistryEntry) loadData.resource).toReloadDataString());
 *
 * En Java los argumentos de un metodo se evaluan ANTES de invocar el
 * metodo. Por tanto toReloadDataString()/toLoadDataString() -- que
 * construyen un String enorme con toda la configuracion anidada de cada
 * especie/familia/propiedad de hoja/suelo -- se ejecutan SIEMPRE, para
 * cada recurso cargado, aunque el logging DEBUG este desactivado o nadie
 * vaya a leer el fichero. LOGGER.debug() puede decidir no escribir la
 * linea, pero para entonces el string caro ya esta construido.
 *
 * El propio mod SI usa el patron correcto en otro sitio (AbstractRegistry,
 * el volcado con el marcador REGISTRY_DUMP), con un Supplier perezoso que
 * solo se evalua si el logging esta realmente activo. Aqui, en
 * JsonRegistryResourceLoader, simplemente no se aplico ese patron.
 *
 * Este Mixin redirige las dos llamadas a toLoadDataString()/
 * toReloadDataString() dentro de postLoad(): si LOGGER.isDebugEnabled()
 * es falso, devuelve un String vacio sin construir nada; si es verdad
 * (alguien esta depurando activamente), llama al metodo real y se
 * comporta exactamente igual que antes. El registro real del recurso
 * (this.registry.register(...), en la rama "else" de postLoad) no se
 * toca -- solo se evita el string de logging.
 *
 * AVISO: aunque este Mixin SI compila contra las clases reales de Dynamic
 * Trees (jar 1.2.2 incluido en libs/ solo para compilar), no se ha podido
 * ejecutar/probar en una instancia real todavia. Probadlo en un mundo de
 * prueba antes de distribuirlo, y revisad si JsonRegistryResourceLoader
 * cambia en una futura actualizacion de Dynamic Trees.
 */
@Pseudo
@Mixin(targets = "com.ferreusveritas.dynamictrees.api.resource.loading.preparation.JsonRegistryResourceLoader", remap = false)
public abstract class JsonRegistryResourceLoaderMixin {

    @Shadow(remap = false)
    private static Logger LOGGER;

    @Redirect(
            method = "postLoad",
            require = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/ferreusveritas/dynamictrees/api/registry/RegistryEntry;toLoadDataString()Ljava/lang/String;",
                    remap = false
            )
    )
    private String bteopt$skipToLoadDataStringUnlessDebug(RegistryEntry<?> registryEntry) {
        return LOGGER.isDebugEnabled() ? registryEntry.toLoadDataString() : "";
    }

    @Redirect(
            method = "postLoad",
            require = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/ferreusveritas/dynamictrees/api/registry/RegistryEntry;toReloadDataString()Ljava/lang/String;",
                    remap = false
            )
    )
    private String bteopt$skipToReloadDataStringUnlessDebug(RegistryEntry<?> registryEntry) {
        return LOGGER.isDebugEnabled() ? registryEntry.toReloadDataString() : "";
    }
}
