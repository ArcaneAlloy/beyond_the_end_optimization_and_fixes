package com.endersjourney.bteoptimizationfixes.mixin.dungeonsgear;

import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dungeons Gear 1.19.2-5.0.6-beta, encantamiento Lucky Explorer (botas): crash "Ticking entity" en el cliente,
 * ClassCastException: ClientLevel cannot be cast to ServerLevel, en LuckyExplorerEnchantment.onPlayerTick.
 *
 * onPlayerTick se ejecuta en los dos lados. Cada 6 bloques andados genera botin con
 * LootTableHelper.generateItemStack((ServerLevel) player.level, ...), sin mirar si es el cliente. En el cliente el
 * mundo es un ClientLevel y el juego se cierra. Ademas el punto de control del cliente empieza en 0 al entrar al mundo,
 * asi que con esas botas puestas crashea nada mas entrar (crash-2026-11-11_02.16.17 y 02.19.58, -5999 42 -2474).
 *
 * Arreglo: en el cliente no se hace nada. El botin lo genera el servidor, como siempre.
 * Verificado por desensamblado de dungeons_gear-1.19.2-5.0.6-beta.jar.
 */
@Pseudo
@Mixin(targets = "com.infamous.dungeons_gear.enchantments.armor.feet.LuckyExplorerEnchantment", remap = false)
public abstract class LuckyExplorerClientGuardMixin {

    @Inject(method = "onPlayerTick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bteopt$serverOnly(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        if (event.player == null || event.player.level.isClientSide) ci.cancel();
    }
}
