package com.endersjourney.bteoptimizationfixes.compat.altarofsummoning;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Altar of Summoning (altar_of_summing-1.19.2-1.1.jar): el altar tiene dureza 1, asi que los jugadores lo pican en un
 * momento y el Coliseo se queda sin altar para invocar a los jefes de Meet Your Fight.
 *
 * Ahora es indestructible como la roca madre, salvo en modo creativo (para poder colocarlo o quitarlo al construir):
 * - Al picarlo no avanza la rotura (velocidad 0, tampoco salen las grietas).
 * - Se cancela la rotura en el servidor por si algo la fuerza (rotura instantanea, otros mods que simulan al jugador).
 * - Las explosiones no lo rompen.
 * - Los mobs que rompen bloques (Wither, Ender Dragon, etc.) no lo rompen.
 *
 * Sin el mod del altar el bloque no existe y no hace nada.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AltarProtection {

    private static final ResourceLocation ALTAR_ID = new ResourceLocation("altar_of_summoning", "altarofsummoning");

    private static Block altar;

    private AltarProtection() {
    }

    private static boolean isAltar(BlockState state) {
        if (altar == null) {
            Block block = ForgeRegistries.BLOCKS.getValue(ALTAR_ID);
            // getValue devuelve aire si el bloque no existe
            altar = block == null ? Blocks.AIR : block;
        }
        return altar != Blocks.AIR && state.is(altar);
    }

    private static boolean canBreak(Player player) {
        return player != null && player.getAbilities().instabuild;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (isAltar(event.getState()) && !canBreak(event.getEntity())) event.setNewSpeed(0F);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (isAltar(event.getState()) && !canBreak(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> isAltar(event.getLevel().getBlockState(pos)));
    }

    @SubscribeEvent
    public static void onMobDestroy(LivingDestroyBlockEvent event) {
        if (isAltar(event.getState())) event.setCanceled(true);
    }
}
