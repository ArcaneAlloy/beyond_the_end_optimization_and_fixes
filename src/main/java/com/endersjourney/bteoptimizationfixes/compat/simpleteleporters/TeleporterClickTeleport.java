package com.endersjourney.bteoptimizationfixes.compat.simpleteleporters;

import jp.artan.teleporters.block.TeleporterBlock;
import jp.artan.teleporters.entity.BlockEntityTeleporter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Teletransporta a UN jugador con el cristal de un teletransportador de
 * Simple Teleporters Reloaded 2.0.0, con la misma logica que usa el propio
 * mod cuando un jugador se agacha encima (BlockEntityTeleporter#teleport):
 *  - el bloque tiene que estar activo (ON != 0) y con cristal enlazado (NBT);
 *  - solo dentro de la misma dimension ("dim" del cristal);
 *  - destino: x + 0.5, y + 1, z + 0.5 del cristal;
 *  - orientacion: la guardada en el cristal ("direction") si la opcion
 *    useDirection de su config esta activa; si no, la actual del jugador;
 *  - sonido de teletransporte de enderman para el jugador.
 *
 * Diferencia consciente: el mod pasa como inclinacion vertical un valor que
 * no es la inclinacion de la camara; aqui se conserva la inclinacion actual
 * del jugador.
 *
 * La opcion useDirection se lee por reflexion: vive en un ConfigEntry de
 * otra libreria (artansprojectcoremod) que no hace falta tener al compilar.
 * Si no se puede leer, se usa la orientacion actual del jugador.
 */
public final class TeleporterClickTeleport {

    private static final String CONFIG_CLASS = "jp.artan.teleporters.config.SimpleTeleportersReloadedConfig";

    private static Method useDirectionGetter;
    private static Object useDirectionEntry;
    private static boolean configLookupDone;

    private TeleporterClickTeleport() {
    }

    public static void teleport(ServerLevel level, BlockState state, ServerPlayer player, BlockEntityTeleporter teleporter) {
        if (state.getValue(TeleporterBlock.ON) == 0) {
            return;
        }
        ItemStack crystal = teleporter.getCrystal();
        if (crystal == null || crystal.isEmpty()) {
            return;
        }
        CompoundTag tag = crystal.getTag();
        if (tag == null || !tag.getString("dim").equals(level.dimension().location().toString())) {
            return;
        }

        float yRot = useDirection() ? tag.getFloat("direction") : player.getYRot();
        player.teleportTo(level, tag.getInt("x") + 0.5F, tag.getInt("y") + 1, tag.getInt("z") + 0.5F,
                yRot, player.getXRot());
        player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, player.getSoundSource(), 1.0F, 1.0F);
    }

    private static boolean useDirection() {
        if (!configLookupDone) {
            configLookupDone = true;
            try {
                Field field = Class.forName(CONFIG_CLASS).getField("CONFIG_USE_DIRECTION");
                useDirectionEntry = field.get(null);
                if (useDirectionEntry != null) {
                    useDirectionGetter = useDirectionEntry.getClass().getMethod("get");
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                useDirectionEntry = null;
                useDirectionGetter = null;
            }
        }
        if (useDirectionEntry == null || useDirectionGetter == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(useDirectionGetter.invoke(useDirectionEntry));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
