package com.endersjourney.bteoptimizationfixes.mixin.geckolib;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import software.bernie.geckolib3.core.IAnimationTickable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lo mismo que AnimatedGeoModelGuiTickMixin, pero para los modelos "con tick" de GeckoLib 3.1.40
 * (AnimatedTickingGeoModel), que no pasan por el codigo de AnimatedGeoModel. Ejemplo: el Mutant Shulker de Mutant More
 * parpadea en la vista previa de Just Enough Resources en JEI.
 *
 * Causa: AnimatedTickingGeoModel#setCustomAnimations usa como reloj animatable.tickTimer() + partialTick, y tickTimer()
 * devuelve el tickCount de la entidad. La entidad de la GUI nunca hace tick, asi que el reloj solo avanza con el
 * partialTick (0..1) y vuelve atras en cada tick: la animacion salta adelante y atras sin parar.
 *
 * Arreglo: para entidades que no estan en el mundo se usa el tiempo de juego del nivel del cliente, que si avanza. Las
 * entidades del mundo no cambian.
 *
 * Verificado con el codigo fuente y el desensamblado de geckolib-forge-1.19-3.1.40.jar (las dos llamadas a
 * IAnimationTickable.tickTimer() de ese metodo) y mutantmore-1.19.2-early_access-2.0.0.jar (MutantShulkerModel
 * extiende AnimatedTickingGeoModel y MutantShulker.tickTimer() devuelve tickCount).
 * @Pseudo + remap = false: sin GeckoLib 3 el Mixin no se aplica.
 */
@Pseudo
@Mixin(targets = "software.bernie.geckolib3.model.AnimatedTickingGeoModel", remap = false)
public abstract class AnimatedTickingGeoModelGuiTickMixin {

    @Redirect(method = "setCustomAnimations(Lsoftware/bernie/geckolib3/core/IAnimatable;ILsoftware/bernie/geckolib3/core/event/predicate/AnimationEvent;)V",
            at = @At(value = "INVOKE", target = "Lsoftware/bernie/geckolib3/core/IAnimationTickable;tickTimer()I", remap = false),
            remap = false, require = 0)
    private int bteopt$animationClock(IAnimationTickable tickable) {
        if (tickable instanceof Entity entity && !entity.isAddedToWorld()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) return (int) minecraft.level.getGameTime();
        }
        return tickable.tickTimer();
    }
}
