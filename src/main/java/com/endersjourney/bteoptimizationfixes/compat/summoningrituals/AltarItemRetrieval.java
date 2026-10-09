package com.endersjourney.bteoptimizationfixes.compat.summoningrituals;

import com.almostreliable.summoningrituals.inventory.AltarInventory;
import net.minecraft.world.item.ItemStack;

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
}
