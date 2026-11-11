package com.endersjourney.bteoptimizationfixes.compat.dungeonsgear;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dungeons Gear 1.19.2-5.0.6-beta, Lucky Explorer: el mod suelta 1 + (nivel - 1) * 2 tiradas de su tabla de botin
 * (siempre 1 esmeralda) cada vez que el jugador se aleja 6 bloques del ultimo punto, sin ninguna probabilidad: 1, 3 o
 * 5 esmeraldas cada 6 bloques (nivel 1, 2 o 3), unas 50 cada 100 bloques con nivel 2. Ademas se ejecutaba tambien en
 * el cliente y lo cerraba (ver LuckyExplorerReworkMixin).
 *
 * Ahora, como dice su descripcion ("Chance to spawn emeralds with every block explored"):
 * - Cada DISTANCE bloques explorados (en linea recta desde el ultimo punto) hay CHANCE_PER_LEVEL * nivel de
 *   probabilidad (10 / 20 / 30 %) de una tirada de la tabla dungeons_gear:enchantments/lucky_explorer (1 esmeralda;
 *   se puede cambiar con un datapack). Con nivel 2, de media 1 esmeralda cada 160 bloques.
 * - Solo en el servidor, con el jugador vivo y fuera de creativo y espectador (como el mod).
 * - El punto de control se guarda en memoria por jugador. Al entrar al mundo (o tras reiniciar) el primer punto es
 *   donde este el jugador, sin premio.
 */
public final class LuckyExplorer {

    public static final int DISTANCE = 32;
    public static final float CHANCE_PER_LEVEL = 0.10F;

    private static final ResourceLocation ENCHANTMENT = new ResourceLocation("dungeons_gear", "lucky_explorer");
    private static final ResourceLocation LOOT_TABLE = new ResourceLocation("dungeons_gear", "enchantments/lucky_explorer");
    private static final Map<UUID, Long> CHECKPOINTS = new ConcurrentHashMap<>();

    private static Enchantment enchantment;

    private LuckyExplorer() {
    }

    public static void tick(Player player) {
        if (!(player.level instanceof ServerLevel level)) return;
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) return;
        if (enchantment == null) {
            enchantment = ForgeRegistries.ENCHANTMENTS.getValue(ENCHANTMENT);
            if (enchantment == null) return;
        }
        int lvl = EnchantmentHelper.getEnchantmentLevel(enchantment, player);
        if (lvl <= 0) return;

        BlockPos pos = player.blockPosition();
        Long last = CHECKPOINTS.get(player.getUUID());
        if (last == null || pos.distSqr(BlockPos.of(last)) >= DISTANCE * DISTANCE) {
            CHECKPOINTS.put(player.getUUID(), pos.asLong());
            if (last == null) return;
        } else {
            return;
        }

        if (player.getRandom().nextFloat() >= CHANCE_PER_LEVEL * lvl) return;
        LootTable table = level.getServer().getLootTables().get(LOOT_TABLE);
        LootContext context = new LootContext.Builder(level)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withRandom(player.getRandom())
                .create(LootContextParamSets.CHEST);
        for (ItemStack stack : table.getRandomItems(context)) {
            level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack));
        }
    }
}
