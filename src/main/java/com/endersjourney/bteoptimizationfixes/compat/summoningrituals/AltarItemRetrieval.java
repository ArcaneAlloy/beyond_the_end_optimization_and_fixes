package com.endersjourney.bteoptimizationfixes.compat.summoningrituals;

import com.almostreliable.summoningrituals.inventory.AltarInventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Saca del altar de Summoning Rituals el ultimo item insertado (o el
 * catalizador) y lo devuelve, en vez de soltarlo en el mundo.
 *
 * Reutiliza el propio AltarInventory#popLastInserted() del mod -- mismo
 * orden de extraccion, mismo manejo de huecos, mismo onContentsChanged()
 * (sincronizacion con el cliente) -- y solo intercepta su llamada final a
 * GameUtils.dropItem(...) (ver AltarInventoryDropCaptureMixin): mientras
 * esta clase esta capturando, el item se guarda aqui en vez de spawnearse.
 *
 * Solo se usa desde el hilo del servidor (AltarBlock#use ya filtra
 * !level.isClientSide), asi que no hace falta nada mas que un flag.
 *
 * Vive fuera del paquete "mixin" a proposito: Mixin prohibe cargar como
 * clases normales las que estan dentro del paquete declarado en el
 * mixins.json.
 */
public final class AltarItemRetrieval {

    private static boolean capturing;
    private static ItemStack captured = ItemStack.EMPTY;

    private AltarItemRetrieval() {
    }

    public static ItemStack takeLastInserted(AltarInventory inventory) {
        capturing = true;
        captured = ItemStack.EMPTY;
        try {
            inventory.popLastInserted();
            return captured;
        } finally {
            capturing = false;
            captured = ItemStack.EMPTY;
        }
    }

    public static boolean isCapturing() {
        return capturing;
    }

    public static void capture(ItemStack stack) {
        captured = stack;
    }

    // --- Proteccion contra la repeticion del click derecho ---------------
    //
    // Con el boton derecho mantenido, el cliente repite "usar" cada 4 ticks.
    // Sin esta proteccion: meter un item deja la mano vacia -> la repeticion
    // lo saca de nuevo; sacarlo deja la mano llena -> la repeticion lo
    // vuelve a meter. Por eso, tras meter (o sacar), la accion contraria se
    // ignora durante REPEAT_GUARD_TICKS, y cada intento ignorado renueva la
    // ventana, de modo que mantener el boton pulsado nunca da la vuelta.

    public enum Action { INSERT, RETRIEVE }

    private static final long REPEAT_GUARD_TICKS = 10L;

    private static final Map<UUID, Action> lastAction = new HashMap<>();
    private static final Map<UUID, Long> lastActionTime = new HashMap<>();

    /**
     * true si la accion pedida es la contraria a la ultima del jugador y
     * llega dentro de la ventana; en ese caso renueva la ventana.
     */
    public static boolean isBlockedRepeat(UUID player, Action action, long gameTime) {
        Action previous = lastAction.get(player);
        Long previousTime = lastActionTime.get(player);
        if (previous == null || previousTime == null || previous == action) {
            return false;
        }
        long elapsed = gameTime - previousTime;
        if (elapsed >= 0 && elapsed < REPEAT_GUARD_TICKS) {
            lastActionTime.put(player, gameTime);
            return true;
        }
        return false;
    }

    public static void record(UUID player, Action action, long gameTime) {
        lastAction.put(player, action);
        lastActionTime.put(player, gameTime);
    }
}
