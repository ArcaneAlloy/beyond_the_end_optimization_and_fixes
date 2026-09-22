package com.endersjourney.bteoptimizationfixes.mixin.euphoriapatcher;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * EuphoriaPatcher (mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher) detecta
 * el shaderpack instalado comparando el NOMBRE DE FICHERO con dos condiciones
 * demasiado estrictas:
 *
 *  1) isBrandNameShader(Path, boolean): exige que el nombre EMPIECE por
 *     "Complementary" (BRAND_NAME). Si el shaderpack lleva un prefijo de
 *     orden de carga (ej. "01_Potato_ComplementaryReimagined_r5.5.1+...",
 *     que es una decision de diseno del pack, no algo que vayamos a quitar),
 *     esta comprobacion falla, la deteccion rapida no encuentra nada, y el
 *     mod cae al escaneo lento por tamano de bytes de TODOS los ficheros de
 *     shaderpacks/ (~9-11s en nuestro caso), terminando ademas en un error
 *     falso de "no tienes ComplementaryShaders_r5.5.1 instalado".
 *
 *  2) checkIfAlreadyInstalled(Path, ShaderInfo): exige que el nombre
 *     contenga literalmente " + EuphoriaPatches_1.6.5" CON ESPACIOS
 *     alrededor del "+". Los ficheros ya parcheados en este pack no lo
 *     llevan (llevan "...+EuphoriaPatches_1.6.5", sin espacios), asi que
 *     aunque el fichero SI este ya parcheado, el mod no lo reconoce como tal.
 *
 * Este Mixin relaja ambas comprobaciones a un simple "contiene la palabra
 * clave", sin exigir prefijo exacto ni espacios exactos. No desactiva
 * ninguna funcionalidad del mod -- el detector simplemente encuentra el
 * fichero que ya esta ahi, en el primer paso rapido, en vez de caer al
 * escaneo lento y despues fallar igualmente.
 *
 * AVISO: no se ha podido compilar contra las clases reales de EuphoriaPatcher
 * en el entorno donde se escribio esto (sin el classpath del mod disponible
 * para compilar). Los nombres de metodo (isBrandNameShader,
 * checkIfAlreadyInstalled) y el string exacto (" + EuphoriaPatches_1.6.5")
 * estan sacados directamente de la version decompilada del jar 1.6.5-r5.5.1,
 * asi que deberian ser correctos para esa version concreta -- pero probadlo
 * en un mundo/instancia de prueba antes de distribuirlo, y revisad si
 * cambian en una futura actualizacion de EuphoriaPatcher (por ejemplo la
 * 1.7.2 que el propio mod anuncia como disponible).
 */
@Pseudo
@Mixin(targets = "mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher", remap = false)
public abstract class EuphoriaPatcherDetectionMixin {

    /**
     * isBrandNameShader(Path, boolean): unica llamada a String#startsWith en
     * todo el metodo (el resto de comprobaciones ya usan contains), asi que
     * el redirect no es ambiguo.
     *
     * Antes: name.startsWith(BRAND_NAME)   -> exige "Complementary..." al principio
     * Ahora: name.contains(BRAND_NAME)     -> vale con "Complementary" en cualquier parte
     */
    @Redirect(
            method = "isBrandNameShader",
            require = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z"
            )
    )
    private boolean ejfixes$brandNameContainsInsteadOfStartsWith(String name, String brandName) {
        return name.contains(brandName);
    }

    /**
     * checkIfAlreadyInstalled(Path, ShaderInfo): el string " + EuphoriaPatches_1.6.5"
     * (con espacios) se usa dos veces en el metodo -- una en el contains() que
     * decide si es un fichero ya parcheado, y otra en el indexOf() que calcula
     * el nombre base. ModifyConstant cambia las dos ocurrencias igual, sin
     * necesitar referenciar el tipo ShaderInfo (que es privado) en absoluto.
     *
     * Efecto secundario menor y sin importancia: el "nombre base" derivado
     * para buscar un .zip sin parchear al lado queda con un "+" residual en
     * vez de quedar limpio. Ese .zip no existe en vuestro caso de todas
     * formas, asi que no cambia nada observable.
     */
    /**
     * checkIfAlreadyInstalled(Path, ShaderInfo) Y el filtro lambda de
     * checkForExistingPatchedShaders(ShaderInfo) comparten literalmente el
     * mismo string " + EuphoriaPatches_1.6.5" (con espacios). Es este
     * segundo sitio -- el lambda de checkForExistingPatchedShaders, la
     * PRIMERA comprobacion que hace detectInstalledShaders() -- el que de
     * verdad decide si se entra en el escaneo lento; isBrandNameShader()
     * nunca iba a coincidir con un fichero ya parcheado de todas formas
     * (tiene su propio "notPatched" que lo excluye a proposito).
     *
     * El lambda se compila como metodo sintetico
     * lambda$checkForExistingPatchedShaders$3 -- confirmado descompilando
     * el .class con --decodelambdas false (la numeracion de lambdas de
     * javac es POR CLASE ENTERA, no por metodo, así que no basta con
     * asumir indice 0; hay otros 3 lambdas antes en el fichero).
     */
    @ModifyConstant(
            method = {"checkIfAlreadyInstalled", "lambda$checkForExistingPatchedShaders$3"},
            require = 3,
            constant = @Constant(stringValue = " + EuphoriaPatches_1.6.5")
    )
    private static String ejfixes$relaxPatchedMarkerSpacing(String original) {
        return "EuphoriaPatches_1.6.5";
    }
}
