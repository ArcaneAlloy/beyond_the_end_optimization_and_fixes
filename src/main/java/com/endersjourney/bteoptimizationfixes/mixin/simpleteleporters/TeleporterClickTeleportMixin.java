package com.endersjourney.bteoptimizationfixes.mixin.simpleteleporters;

import com.endersjourney.bteoptimizationfixes.compat.simpleteleporters.TeleporterClickTeleport;
import jp.artan.teleporters.entity.BlockEntityTeleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Simple Teleporters Reloaded 2.0.0: TeleporterBlock#use (m_6227_ en SRG,
 * por eso remap = false). Con cristal dentro y la mano principal vacia, el
 * original devolvia el cristal al jugador. Ahora:
 *  - el cristal ya no se puede sacar con click (ni normal ni con Shift);
 *  - el click derecho teletransporta al jugador, igual que agacharse encima
 *    (ver TeleporterClickTeleport).
 *
 * Meter un cristal con el cristal en la mano funciona igual que antes.
 * Se cancela en cliente y servidor, para que el cliente tampoco "prediga"
 * que el cristal pasa a la mano; el teletransporte solo ocurre en servidor.
 *
 * Verificado por desensamblado del jar real
 * (SimpleTeleportersReloaded-forge-mc1.19.2-2.0.0.jar).
 */
@Pseudo
@Mixin(targets = "jp.artan.teleporters.block.TeleporterBlock", remap = false)
public abstract class TeleporterClickTeleportMixin {

    @Inject(method = "m_6227_", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$teleportInsteadOfTakingCrystal(BlockState state, Level level, BlockPos pos, Player player,
                                                       InteractionHand hand, BlockHitResult hit,
                                                       CallbackInfoReturnable<InteractionResult> cir) {
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof BlockEntityTeleporter teleporter) || !teleporter.hasCrystal()) {
            return;
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            TeleporterClickTeleport.teleport(serverLevel, state, serverPlayer, teleporter);
        }
        cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
