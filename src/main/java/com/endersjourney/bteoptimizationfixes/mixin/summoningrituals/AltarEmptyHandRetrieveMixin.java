package com.endersjourney.bteoptimizationfixes.mixin.summoningrituals;

import com.almostreliable.summoningrituals.platform.PlatformBlockEntity;
import com.endersjourney.bteoptimizationfixes.compat.summoningrituals.AltarItemRetrieval;
import com.endersjourney.bteoptimizationfixes.compat.summoningrituals.AltarItemRetrieval.Action;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Summoning Rituals (summoningrituals-forge-1.19.2-2.0.7): cambia como se
 * recuperan los items insertados en el altar.
 *
 * Original: con la mano principal vacia, solo Shift + click derecho saca el
 * ultimo item insertado (o el catalizador), y lo suelta en el suelo encima
 * del altar. El click derecho normal no hace nada.
 *
 * Con este Mixin, con la mano principal vacia:
 *  - Click derecho normal: saca el ultimo item insertado (o el catalizador)
 *    y lo pone directamente en la mano principal del jugador.
 *  - Shift + click derecho: no hace nada.
 *
 * Como funciona: AltarBlock#use (servidor, mano principal) hace
 *
 *     player.setMainHandItem(altar.handleInteraction(player, mainHandItem));
 *
 * asi que basta con que handleInteraction devuelva el item extraido para
 * que acabe en la mano. Este @Inject en HEAD solo actua en la rama de
 * "mano vacia" y sin ritual en curso; con un ritual en curso o con un item
 * en la mano se deja correr el metodo original (mensaje de "en progreso",
 * insercion de items, catalizador, etc. sin cambios).
 *
 * Proteccion contra la repeticion del click derecho (el cliente repite
 * "usar" cada 4 ticks con el boton mantenido): tras meter un item, sacar
 * queda bloqueado medio segundo, y viceversa (ver AltarItemRetrieval).
 * Sin esto, el mismo click metia el item y la repeticion lo volvia a sacar.
 *
 * La extraccion reutiliza el popLastInserted() del propio mod a traves de
 * AltarItemRetrieval + AltarInventoryDropCaptureMixin, que convierte su
 * dropItem final en una captura.
 *
 * Verificado por desensamblado del jar real (AltarBlock#use ->
 * AltarBlockEntity#handleInteraction -> AltarInventory#popLastInserted).
 * Revisadlo si se actualiza Summoning Rituals.
 */
@Pseudo
@Mixin(targets = "com.almostreliable.summoningrituals.altar.AltarBlockEntity", remap = false)
public abstract class AltarEmptyHandRetrieveMixin {

    @Inject(method = "handleInteraction", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$retrieveToHand(ServerPlayer player, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (player == null) {
            return;
        }
        PlatformBlockEntity altar = (PlatformBlockEntity) (Object) this;
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        Level level = blockEntity.getLevel();
        if (level == null || altar.getProgress() > 0) {
            return;
        }
        UUID playerId = player.getUUID();
        long now = level.getGameTime();

        if (!stack.isEmpty()) {
            // Con item en la mano: insercion original, salvo que sea la
            // repeticion del click que acaba de sacar este mismo item.
            if (AltarItemRetrieval.isBlockedRepeat(playerId, Action.INSERT, now)) {
                cir.setReturnValue(stack);
                return;
            }
            AltarItemRetrieval.record(playerId, Action.INSERT, now);
            return;
        }

        if (player.isShiftKeyDown()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        // Mano vacia: ignorar la repeticion del click que acaba de meter un item.
        if (AltarItemRetrieval.isBlockedRepeat(playerId, Action.RETRIEVE, now)) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        ItemStack taken = AltarItemRetrieval.takeLastInserted(altar.getInventory());
        if (!taken.isEmpty()) {
            AltarItemRetrieval.record(playerId, Action.RETRIEVE, now);
            level.playSound(null, blockEntity.getBlockPos(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                    0.2F, (level.random.nextFloat() - level.random.nextFloat()) * 1.4F + 2.0F);
        }
        cir.setReturnValue(taken);
    }
}
