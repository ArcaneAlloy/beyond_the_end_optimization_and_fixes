package com.endersjourney.bteoptimizationfixes.mixin.geckolib;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GeckoLib 3.1.40: con el juego en pausa, los modelos de algunos mobs (Dungeons Mobs: Blastling, Enderlings...) se
 * vuelven locos, con la cabeza girando sin parar.
 *
 * Causa: en pausa AnimatedGeoModel#setCustomAnimations no avanza el seekTime, y AnimationProcessor#tickAnimation sale
 * sin hacer nada si el seekTime es igual al del frame anterior ("entity already animated on this tick"). Asi que los
 * huesos no se vuelven a calcular y conservan lo que tenian. Los modelos que giran la cabeza hacia donde mira el mob
 * lo hacen sumando a la rotacion actual del hueso (head.setRotationX(head.getRotationX() + headPitch...)), asi que en
 * pausa esa suma se acumula frame tras frame.
 *
 * Arreglo: en pausa se olvida el ultimo seekTime al entrar en tickAnimation, para que los huesos se recalculen cada
 * frame igual que sin pausa (mismo seekTime, misma pose) y la suma de la cabeza se aplique sobre la pose limpia.
 * Fuera de pausa no cambia nada.
 *
 * Verificado con el codigo fuente de geckolib-forge-1.19-3.1.40.jar y el desensamblado de
 * dungeons_mobs-1.19.2-4.0.8-beta.jar (BlastlingModel#setCustomAnimations).
 * @Pseudo + remap = false: sin GeckoLib 3 el Mixin no se aplica.
 */
@Pseudo
@Mixin(targets = "software.bernie.geckolib3.core.processor.AnimationProcessor", remap = false)
public abstract class AnimationProcessorPauseMixin {

    @Shadow(remap = false)
    private double lastTickValue;

    @Inject(method = "tickAnimation", at = @At("HEAD"), remap = false)
    private void bteopt$reanimateWhilePaused(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.isPaused()) {
            // NaN nunca es igual al seekTime: tickAnimation vuelve a procesar las animaciones
            this.lastTickValue = Double.NaN;
        }
    }
}
