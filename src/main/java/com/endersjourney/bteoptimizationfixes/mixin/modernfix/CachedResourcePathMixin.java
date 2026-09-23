package com.endersjourney.bteoptimizationfixes.mixin.modernfix;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * ModernFix's CachedResourcePath.getFullPath(int) reconstruye el String de
 * la ruta completa con un StringBuilder CADA VEZ que se llama, pese a que
 * el objeto es inmutable (pathComponents es final, fijado una sola vez en
 * el constructor). El unico sitio de todo ModernFix que llama a este
 * metodo es PackResourcesCacheEngine.getResources(), SIEMPRE con
 * startIndex=2, y ese metodo se invoca una vez por cada tipo de registry
 * que Minecraft carga desde los datapacks al abrir la pantalla de crear
 * mundo (biomas, estructuras, density functions, etc. -- mas de una
 * docena de tipos), recorriendo la MISMA lista de CachedResourcePath cada
 * vez. Resultado: el mismo string se reconstruye una vez por cada tipo de
 * registry, para cada fichero de cada namespace de cada mod -- confirmado
 * con perfilador (JFR): ~14% de las muestras de CPU de esa ventana caian
 * aqui.
 *
 * Este Mixin reescribe getFullPath(int) para memorizar el resultado la
 * primera vez que se calcula para un startIndex dado, y devolver el
 * mismo String en llamadas posteriores con el mismo startIndex -- sin
 * cambiar el resultado para ningun llamador, solo evitando reconstruirlo
 * cuando ya se conoce.
 *
 * AVISO: verificado por descompilacion directa del jar real
 * (modernfix-forge-5_18_1_mc1_19_2.jar) -- el cuerpo del metodo y el
 * campo pathComponents son exactos para esa version. Compilalo y
 * pruebalo antes de distribuirlo; revisad si cambia en una futura
 * actualizacion de ModernFix.
 */
@Pseudo
@Mixin(targets = "org.embeddedt.modernfix.resources.CachedResourcePath", remap = false)
public abstract class CachedResourcePathMixin {

    @Shadow(remap = false)
    @Final
    private String[] pathComponents;

    @Unique
    private String bteopt$cachedFullPath;

    @Unique
    private int bteopt$cachedFullPathStartIndex = Integer.MIN_VALUE;

    @Overwrite
    public String getFullPath(int startIndex) {
        if (this.bteopt$cachedFullPath != null && this.bteopt$cachedFullPathStartIndex == startIndex) {
            return this.bteopt$cachedFullPath;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < this.pathComponents.length; ++i) {
            sb.append(this.pathComponents[i]);
            if (i != this.pathComponents.length - 1) {
                sb.append('/');
            }
        }
        String result = sb.toString();
        this.bteopt$cachedFullPath = result;
        this.bteopt$cachedFullPathStartIndex = startIndex;
        return result;
    }
}
