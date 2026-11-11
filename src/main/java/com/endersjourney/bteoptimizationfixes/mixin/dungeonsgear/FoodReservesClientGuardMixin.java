package com.endersjourney.bteoptimizationfixes.mixin.dungeonsgear;

import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dungeons Gear 1.19.2-5.0.6-beta, encantamiento Food Reserves (casco): al terminar de beber una pocion de curacion
 * hace (ServerLevel) player.level sin mirar el lado. El evento tambien salta en el cliente para el jugador local, asi
 * que el cliente crashea igual que con Lucky Explorer. En el cliente no se hace nada.
 * Verificado por desensamblado de dungeons_gear-1.19.2-5.0.6-beta.jar.
 */
@Pseudo
@Mixin(targets = "com.infamous.dungeons_gear.enchantments.armor.head.FoodReservesEnchantment", remap = false)
public abstract class FoodReservesClientGuardMixin {

    @Inject(method = "onPlayerUsedHealthPotion", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bteopt$serverOnly(LivingEntityUseItemEvent.Finish event, CallbackInfo ci) {
        if (event.getEntity() == null || event.getEntity().level.isClientSide) ci.cancel();
    }
}
