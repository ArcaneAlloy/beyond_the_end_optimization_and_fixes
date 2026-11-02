package com.endersjourney.bteoptimizationfixes.mixin.oculus;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Oculus (Iris) 1.6.9.3 compila el pipeline de shaders del Overworld al abrir
 * el menu principal: Iris.onLoadingComplete(), llamado al final de
 * TitleScreen.init, hace
 *
 *     lastDimension = DimensionId.OVERWORLD;
 *     getPipelineManager().preparePipeline(DimensionId.OVERWORLD);
 *
 * Con Complementary + Euphoria eso son ~6 s en el hilo de render (medido con
 * JFR), y el menu no aparece hasta que termina.
 *
 * Ese pipeline se tira siempre sin usarse. Al entrar en un mundo,
 * MixinMinecraft_PipelineManagement (en updateLevelInEngines) compara
 * getCurrentDimension() con lastDimension POR REFERENCIA (if_acmpeq), y
 * getCurrentDimension() crea un NamespacedId nuevo en cada llamada cuando ya
 * hay nivel. Nunca es el mismo objeto que DimensionId.OVERWORLD, asi que llama
 * a destroyPipeline() (vacia pipelinesPerDimension) y compila otro desde cero.
 *
 * Aqui se corta onLoadingComplete justo despues de asignar lastDimension, antes
 * de getPipelineManager().preparePipeline(...). Que no haya pipeline en el menu
 * es un estado que Oculus ya soporta: Iris.reload() (cambiar de shader desde el
 * menu) tampoco prepara pipeline si no hay nivel, getPipeline() devuelve un
 * Optional y MixinLevelRenderer lo prepara al primer frame del mundo si falta.
 *
 * Resultado: el menu sale ~6 s antes con shaders y el pipeline se compila una
 * sola vez, al entrar al mundo (como ya pasaba). Sin shaders activos no cambia
 * nada apreciable. Sin Oculus instalado el Mixin no se aplica (@Pseudo).
 */
@Pseudo
@Mixin(targets = "net.coderbot.iris.Iris", remap = false)
public abstract class OculusTitleScreenPipelineSkipMixin {

    @Inject(
            method = "onLoadingComplete",
            cancellable = true,
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/coderbot/iris/Iris;getPipelineManager()Lnet/coderbot/iris/pipeline/PipelineManager;",
                    ordinal = 0
            )
    )
    private static void ejfixes$skipTitleScreenPipeline(CallbackInfo ci) {
        ci.cancel();
    }
}
