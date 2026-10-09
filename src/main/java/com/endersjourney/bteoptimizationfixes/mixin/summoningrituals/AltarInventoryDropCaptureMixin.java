package com.endersjourney.bteoptimizationfixes.mixin.summoningrituals;

import com.almostreliable.summoningrituals.util.GameUtils;
import com.endersjourney.bteoptimizationfixes.compat.summoningrituals.AltarItemRetrieval;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Summoning Rituals 2.0.7: AltarInventory#popLastInserted() termina siempre
 * con GameUtils.dropItem(level, pos, stack, true), que spawnea el item
 * encima del altar. Las dos ramas del metodo (catalizador / ultimo item de
 * la cola) llaman a dropItem justo despues de haber quitado ya el item del
 * inventario, asi que capturar ese stack es seguro: ya no pertenece al
 * altar.
 *
 * Si AltarItemRetrieval esta capturando (click derecho con la mano vacia,
 * ver AltarEmptyHandRetrieveMixin), el stack se le entrega para ir a la
 * mano del jugador. En cualquier otro caso se llama al dropItem original
 * sin cambios.
 *
 * Verificado por desensamblado del jar real. Revisadlo si se actualiza
 * Summoning Rituals.
 */
@Pseudo
@Mixin(targets = "com.almostreliable.summoningrituals.inventory.AltarInventory", remap = false)
public abstract class AltarInventoryDropCaptureMixin {

    @Redirect(
            method = "popLastInserted",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/almostreliable/summoningrituals/util/GameUtils;dropItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;Z)V",
                    remap = false
            ),
            remap = false
    )
    private void bteopt$captureInsteadOfDrop(Level level, BlockPos pos, ItemStack stack, boolean center) {
        if (AltarItemRetrieval.isCapturing()) {
            AltarItemRetrieval.capture(stack);
        } else {
            GameUtils.dropItem(level, pos, stack, center);
        }
    }
}
