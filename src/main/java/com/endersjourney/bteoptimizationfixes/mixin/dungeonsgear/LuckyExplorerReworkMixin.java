package com.endersjourney.bteoptimizationfixes.mixin.dungeonsgear;

import com.endersjourney.bteoptimizationfixes.compat.dungeonsgear.LuckyExplorer;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dungeons Gear 1.19.2-5.0.6-beta, encantamiento Lucky Explorer (botas): se sustituye su onPlayerTick entero por
 * LuckyExplorer.tick (ver alli el porque).
 *
 * Tambien arregla el crash "Ticking entity: ClientLevel cannot be cast to ServerLevel": el original se ejecutaba en
 * los dos lados y hacia (ServerLevel) player.level; con esas botas el cliente se cerraba nada mas entrar al mundo
 * (crash-2026-11-11_02.16.17 y 02.19.58). LuckyExplorer.tick solo hace algo en el servidor.
 *
 * Verificado por desensamblado de dungeons_gear-1.19.2-5.0.6-beta.jar.
 */
@Pseudo
@Mixin(targets = "com.infamous.dungeons_gear.enchantments.armor.feet.LuckyExplorerEnchantment", remap = false)
public abstract class LuckyExplorerReworkMixin {

    @Inject(method = "onPlayerTick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bteopt$rework(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        ci.cancel();
        if (event.phase == TickEvent.Phase.END && event.player != null) LuckyExplorer.tick(event.player);
    }
}
