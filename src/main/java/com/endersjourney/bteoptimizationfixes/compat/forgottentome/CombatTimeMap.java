package com.endersjourney.bteoptimizationfixes.compat.forgottentome;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sustituye a ForgottenTomeMod.damageGroup (jugador -> tick del ultimo golpe recibido). Ver ForgottenTomeCombatMapMixin.
 *
 * - Todos los metodos van sincronizados: en un mundo de un jugador el mapa lo usan a la vez el hilo del servidor
 *   (LivingAttackEvent lo escribe) y el hilo de render (el tick del jugador del cliente y el boton del inventario lo leen).
 * - get() nunca devuelve null. Forgotten Tome Menu siempre hace containsKey() y luego get().longValue(); si entre las dos
 *   llamadas el servidor ha hecho remove() (LivingAttackEvent hace remove() + put()), get() devolvia null y el juego
 *   crasheaba. Ahora, si no hay entrada, devuelve un tick tan antiguo que cuenta como "fuera de combate"; en el tick
 *   siguiente ya se lee el valor nuevo.
 */
public final class CombatTimeMap extends HashMap<UUID, Long> {

    /** Muy en el pasado: (tick actual - esto) siempre supera los 600 ticks de combate, sin desbordar. */
    private static final Long NOT_IN_COMBAT = Long.MIN_VALUE / 4;

    public CombatTimeMap() {
    }

    public CombatTimeMap(Map<UUID, Long> copy) {
        super(copy);
    }

    @Override
    public synchronized Long get(Object key) {
        Long value = super.get(key);
        return value != null ? value : NOT_IN_COMBAT;
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        return super.containsKey(key);
    }

    @Override
    public synchronized Long put(UUID key, Long value) {
        return super.put(key, value);
    }

    @Override
    public synchronized Long remove(Object key) {
        return super.remove(key);
    }

    @Override
    public synchronized void clear() {
        super.clear();
    }

    @Override
    public synchronized int size() {
        return super.size();
    }
}
