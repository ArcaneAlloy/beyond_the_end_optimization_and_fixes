package com.endersjourney.bteoptimizationfixes.mixin.advancementplaques;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Advancement Plaques 1.4.7: AdvancementPlaque#drawPlaque hace
 * RenderSystem.setShaderColor(1, 1, 1, alpha) para el fundido de la placa y
 * del destello, y nunca lo devuelve a (1, 1, 1, 1). Ese color se queda
 * puesto para el siguiente frame y tiñe/transparenta lo que se dibuja con
 * el color del shader sin fijarlo antes (el cielo, entre otros): por eso el
 * cielo "parpadea" de color mientras se ve la placa de un logro.
 *
 * Al terminar render(...) se restaura el color del shader a blanco opaco.
 *
 * Verificado por desensamblado del jar real (AdvancementPlaques-1.19.2-1.4.7.jar).
 * @Pseudo + remap = false: sin Advancement Plaques el Mixin no se aplica.
 */
@Pseudo
@Mixin(targets = "com.anthonyhilyard.advancementplaques.AdvancementPlaque", remap = false)
public abstract class AdvancementPlaqueShaderColorMixin {

    @Inject(method = "render(IILcom/mojang/blaze3d/vertex/PoseStack;)Z", at = @At("RETURN"), remap = false)
    private void bteopt$resetShaderColor(int screenWidth, int index, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                         CallbackInfoReturnable<Boolean> cir) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
