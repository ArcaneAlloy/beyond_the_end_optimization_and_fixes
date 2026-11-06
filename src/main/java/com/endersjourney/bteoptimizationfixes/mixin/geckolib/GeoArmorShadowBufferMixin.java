package com.endersjourney.bteoptimizationfixes.mixin.geckolib;

import com.endersjourney.bteoptimizationfixes.compat.geckolib.client.ArmorLayerBufferSource;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * GeckoLib 3.1.40 + Oculus con sombras: en primera persona aparece la armadura de GeckoLib (Dungeons Gear...) flotando
 * en medio de la pantalla. Desactivar las sombras en tiempo real lo "arregla".
 *
 * Causa: GeoArmorRenderer.render(float, PoseStack, VertexConsumer, int) no dibuja en el buffer que le pasa la capa de
 * armaduras, sino que coge siempre el buffer principal del juego (Minecraft.getInstance().renderBuffers()
 * .bufferSource()). Oculus dibuja al jugador tambien en el pase de sombras (aunque estes en primera persona), con su
 * propio buffer: el resto del jugador va a ese buffer, pero la armadura de GeckoLib se cuela en el buffer principal con
 * las posiciones calculadas para el mapa de sombras, y luego se pinta en la vista normal en un sitio que no le toca.
 *
 * Arreglo: mientras la capa de armaduras se esta dibujando, la armadura de GeckoLib usa el mismo buffer que ella (el
 * del pase de sombras durante las sombras, el normal en la vista normal), igual que las armaduras de vanilla. Fuera de
 * la capa de armaduras (ExtendedGeoEntityRenderer, que ya le pasa su buffer correcto) no cambia nada.
 *
 * Verificado con el codigo fuente y el desensamblado de geckolib-forge-1.19-3.1.40.jar. @Pseudo + remap = false: sin
 * GeckoLib 3 el Mixin no se aplica.
 */
@Pseudo
@Mixin(targets = "software.bernie.geckolib3.renderers.geo.GeoArmorRenderer", remap = false)
public abstract class GeoArmorShadowBufferMixin {

    /** El buffer del brillo de encantamiento/armadura: ItemRenderer.getArmorFoilBuffer(bufferSource, ...). */
    @ModifyArg(
            method = "render(FLcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;m_115184_(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;ZZ)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
                    remap = false
            ),
            index = 0,
            remap = false,
            require = 0
    )
    private MultiBufferSource bteopt$foilBufferSource(MultiBufferSource original) {
        MultiBufferSource layer = ArmorLayerBufferSource.current();
        return layer != null ? layer : original;
    }

    /** El buffer que se pasa al render del modelo (lo usan las capas extra, p. ej. partes que brillan). */
    @ModifyArg(
            method = "render(FLcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lsoftware/bernie/geckolib3/renderers/geo/GeoArmorRenderer;render(Lsoftware/bernie/geckolib3/geo/render/built/GeoModel;Ljava/lang/Object;FLnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
                    remap = false
            ),
            index = 5,
            remap = false,
            require = 0
    )
    private MultiBufferSource bteopt$modelBufferSource(MultiBufferSource original) {
        MultiBufferSource layer = ArmorLayerBufferSource.current();
        return layer != null ? layer : original;
    }
}
