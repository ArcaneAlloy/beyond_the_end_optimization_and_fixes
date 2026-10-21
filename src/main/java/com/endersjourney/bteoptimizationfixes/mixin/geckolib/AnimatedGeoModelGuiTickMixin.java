package com.endersjourney.bteoptimizationfixes.mixin.geckolib;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * GeckoLib 3.1.40: los mobs que se dibujan fuera del mundo (la vista previa de Just Enough Resources en JEI, y otras
 * GUIs parecidas) tiemblan saltando entre poses. Pasa con Mutant More, Dungeons Mobs, etc.
 *
 * Causa: AnimatedGeoModel#setCustomAnimations usa como reloj de la animacion entity.tickCount + partialTick. Esas
 * entidades de GUI no estan en el mundo y nunca hacen tick, asi que su tickCount se queda fijo y el reloj solo avanza
 * con el partialTick (0..1) y vuelve atras en cada tick: la animacion va adelante y atras sin parar. (JEI no pausa el
 * juego, asi que AnimationProcessorPauseMixin no interviene aqui.)
 *
 * Arreglo: para entidades que no estan en el mundo, en vez de su tickCount se usa el tiempo de juego del nivel del
 * cliente, que si avanza. Las entidades del mundo no cambian.
 *
 * El campo se indica con su nombre SRG (f_19797_ = Entity.tickCount) y remap = false, igual que en el jar de GeckoLib.
 * Verificado con el codigo fuente y el desensamblado de geckolib-forge-1.19-3.1.40.jar y
 * JustEnoughResources-1.19.2-1.2.3.243.jar (RenderHelper#renderEntity no hace tick de la entidad).
 * @Pseudo + remap = false: sin GeckoLib 3 el Mixin no se aplica.
 */
@Pseudo
@Mixin(targets = "software.bernie.geckolib3.model.AnimatedGeoModel", remap = false)
public abstract class AnimatedGeoModelGuiTickMixin {

    @Redirect(method = "setCustomAnimations(Lsoftware/bernie/geckolib3/core/IAnimatable;ILsoftware/bernie/geckolib3/core/event/predicate/AnimationEvent;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;f_19797_:I", opcode = 180 /* GETFIELD */, remap = false),
            remap = false)
    private int bteopt$animationClock(Entity entity) {
        if (entity.isAddedToWorld()) {
            return entity.tickCount;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null ? (int) minecraft.level.getGameTime() : entity.tickCount;
    }
}
