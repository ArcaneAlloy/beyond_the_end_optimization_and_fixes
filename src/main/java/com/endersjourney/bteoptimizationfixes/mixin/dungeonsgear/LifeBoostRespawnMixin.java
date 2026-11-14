package com.endersjourney.bteoptimizationfixes.mixin.dungeonsgear;

import net.minecraftforge.event.entity.player.PlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dungeons Gear 1.19.2-5.0.6-beta, Life Boost (baneado, ver LifeBoostPurge): su onRespawn sumaba +1 corazón por nivel a
 * la vida máxima en cada reaparición, sin tope. Se anula entero, por si algún objeto de un mundo antiguo aún lo lleva
 * cuando el jugador muere antes de que LifeBoostPurge lo limpie.
 *
 * Verificado por desensamblado de dungeons_gear-1.19.2-5.0.6-beta.jar.
 */
@Pseudo
@Mixin(targets = "com.infamous.dungeons_gear.enchantments.armor.chest.LifeBoostEnchantment", remap = false)
public abstract class LifeBoostRespawnMixin {

    @Inject(method = "onRespawn", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bteopt$disable(PlayerEvent.Clone event, CallbackInfo ci) {
        ci.cancel();
    }
}
