package com.endersjourney.bteoptimizationfixes.mixin.mimic;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mimic 1.2.38: MimicChaseGoal.tick (m_8037_) llama a getNavigation().moveTo(objetivo, velocidad) en CADA tick. Cada
 * llamada recalcula el camino y, ademas, reinicia la deteccion de atascos de PathNavigation (moveTo pone a cero
 * lastStuckCheck). Resultado: si se queda atascado contra un bloque nunca se da cuenta y sigue empujando contra el
 * mismo sitio para siempre.
 *
 * Ahora, como el ataque cuerpo a cuerpo de vanilla (MeleeAttackGoal), solo recalcula el camino cada 10 ticks o cuando
 * se ha quedado sin camino. Entre medias sigue el camino que ya tiene, y la deteccion de atascos de vanilla puede
 * abandonar un camino imposible para que el siguiente recalculo busque otro.
 *
 * Verificado por desensamblado de Mimic-forge-1.19.1-1.2.38.jar (la unica llamada a moveTo de ese metodo).
 */
@Pseudo
@Mixin(targets = "ca.lukegrahamlandry.mimic.goals.MimicChaseGoal", remap = false)
public abstract class MimicChaseRepathMixin {

    @Unique
    private static final int bteopt$REPATH_TICKS = 10;

    @Unique
    private int bteopt$repathCooldown;

    @Redirect(
            method = "m_8037_",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;m_5624_(Lnet/minecraft/world/entity/Entity;D)Z",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private boolean bteopt$throttledMoveTo(PathNavigation navigation, Entity target, double speed) {
        if (this.bteopt$repathCooldown > 0) this.bteopt$repathCooldown--;
        if (this.bteopt$repathCooldown > 0 && !navigation.isDone()) return true;
        this.bteopt$repathCooldown = bteopt$REPATH_TICKS;
        return navigation.moveTo(target, speed);
    }
}
