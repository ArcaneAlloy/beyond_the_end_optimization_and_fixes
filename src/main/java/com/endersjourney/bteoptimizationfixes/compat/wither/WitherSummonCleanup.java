package com.endersjourney.bteoptimizationfixes.compat.wither;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * En el modpack, al invocar al Wither (calaveras + piedra de almas de Bygone Nether) a veces se quedan en el mundo
 * parte de las calaveras y de los bloques de la base, cuando vanilla deberia quitar la figura entera.
 *
 * Al aparecer un Wither recien invocado (aun con su tiempo de invulnerabilidad inicial y no cargado desde disco),
 * se quitan los restos de la figura: calaveras de Wither y bloques de base (#minecraft:wither_summon_base_blocks)
 * en la zona que ocupa la T. Vanilla coloca al Wither en el bloque inferior de la T, asi que la figura esta en
 * x/z -1..+1 y en y 0..+2 respecto a el. Se hace al aparecer y otra vez en el tick siguiente, por si algo los vuelve
 * a poner.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WitherSummonCleanup {

    /** WitherBoss#makeInvulnerable pone 220 ticks al invocarlo. */
    private static final int FRESH_SUMMON_TICKS = 200;

    private WitherSummonCleanup() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof WitherBoss wither)) return;
        if (event.loadedFromDisk() || wither.getInvulnerableTicks() < FRESH_SUMMON_TICKS) return;

        BlockPos origin = wither.blockPosition();
        clearPattern(level, origin);
        level.getServer().tell(new TickTask(level.getServer().getTickCount() + 1, () -> clearPattern(level, origin)));
    }

    private static void clearPattern(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(1, 2, 1))) {
            BlockState state = level.getBlockState(pos);
            boolean skull = state.is(Blocks.WITHER_SKELETON_SKULL) || state.is(Blocks.WITHER_SKELETON_WALL_SKULL);
            if (skull || state.is(BlockTags.WITHER_SUMMON_BASE_BLOCKS)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                level.levelEvent(2001, pos, Block.getId(state));
            }
        }
    }
}
