package com.endersjourney.bteoptimizationfixes.mixin.forgottentome;

import com.endersjourney.bteoptimizationfixes.compat.forgottentome.CombatTimeMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.UUID;

/**
 * Forgotten Tome Menu 1.6.0: crash "Ticking entity" en un mundo de un jugador, al recibir golpes:
 * NullPointerException: Cannot invoke "java.lang.Long.longValue()" because the return value of
 * "java.util.HashMap.get(Object)" is null, en Player.forgotten_tome_tick (PlayerMixin del mod).
 *
 * Causa: ForgottenTomeMod.damageGroup es un HashMap estatico normal que comparten el hilo del servidor y el del cliente.
 * Cuando al jugador le pegan, LivingAttackEvent (hilo del servidor) hace damageGroup.remove(uuid) y luego
 * damageGroup.put(uuid, tick). A la vez, el tick del jugador del cliente (hilo de render) hace containsKey(uuid) y luego
 * get(uuid).longValue(). Si el remove() cae justo entre esas dos llamadas, get() devuelve null y el juego crashea.
 * Ademas, un HashMap modificado desde dos hilos a la vez puede corromperse.
 *
 * Arreglo: al cargar la clase del mod se cambia damageGroup por un CombatTimeMap (mismos datos, metodos sincronizados y
 * get() que nunca devuelve null). No cambia nada mas del mod: el combate sigue durando 600 ticks (30 s) igual que antes.
 *
 * Verificado por desensamblado de forgotten_tome_menu-1.6.0.jar: damageGroup es "public static final HashMap<UUID,Long>",
 * se crea en el inicializador estatico y solo se usa con containsKey, get, put y remove.
 */
@Pseudo
@Mixin(targets = "com.binaris.forgotten_tome_menu.ForgottenTomeMod", remap = false)
public abstract class ForgottenTomeCombatMapMixin {

    @Shadow(remap = false)
    @Final
    @Mutable
    public static HashMap<UUID, Long> damageGroup;

    @Inject(method = "<clinit>", at = @At("RETURN"), remap = false)
    private static void bteopt$threadSafeCombatMap(CallbackInfo ci) {
        if (damageGroup != null && !(damageGroup instanceof CombatTimeMap)) {
            damageGroup = new CombatTimeMap(damageGroup);
        }
    }
}
