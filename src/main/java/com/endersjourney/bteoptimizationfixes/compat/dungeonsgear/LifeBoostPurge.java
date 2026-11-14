package com.endersjourney.bteoptimizationfixes.compat.dungeonsgear;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;

/**
 * Dungeons Gear 1.19.2-5.0.6-beta, Life Boost (peto): baneado del modpack. Cada vez que el jugador reaparecía sumaba
 * +1 corazón por nivel (hasta 3) a la vida máxima, para siempre y sin tope, y contaba casi cualquier muerte (caídas,
 * lava, vacío...): bastaba con morir a propósito unas cuantas veces.
 * <p>
 * Que no salga ni se pueda aplicar lo hace la propia config de Dungeons Gear (enchantmentBlacklist en
 * dungeons_gear-common.toml: loot, comercios, mesa, yunque y libros); las recetas del Warlock se quitan en BTE Mobs.
 * Esto limpia los mundos antiguos:
 * <ul>
 *   <li>Quita el encantamiento de los objetos del jugador (inventario, armadura y mano secundaria) y de los de cualquier
 *   contenedor que abra. Un libro encantado que solo tenía Life Boost queda como libro normal.</li>
 *   <li>Quita la vida máxima extra que ya se hubiera ganado (modificador del mod) y ajusta la vida actual.</li>
 *   <li>El efecto al reaparecer se anula en LifeBoostRespawnMixin.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LifeBoostPurge {

    private static final ResourceLocation ENCHANTMENT = new ResourceLocation("dungeons_gear", "life_boost");
    /** UUID del modificador de vida máxima de Life Boost (LifeBoostEnchantment.LIFE_BOOST). */
    private static final UUID MODIFIER = UUID.fromString("ddea725c-1b2e-432e-b96d-871526e69606");
    /** Cada cuánto se revisa el inventario del jugador. */
    private static final int INTERVAL = 40;

    private LifeBoostPurge() {}

    private static Enchantment enchantment() {
        return ForgeRegistries.ENCHANTMENTS.getValue(ENCHANTMENT);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) purge(player);
    }

    /** Tras reaparecer (y volver del End), por si el jugador traía la vida extra en el original. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) removeModifier(player);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % INTERVAL != 0) return;
        purge(player);
    }

    /** Objetos de cualquier contenedor que se abra (cofres de mundos antiguos, mochilas...). */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        Enchantment ench = enchantment();
        if (ench == null || !(event.getEntity() instanceof ServerPlayer)) return;
        AbstractContainerMenu menu = event.getContainer();
        boolean changed = false;
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            ItemStack clean = strip(stack, ench);
            if (clean != stack) {
                slot.set(clean);
                changed = true;
            }
        }
        if (changed) menu.broadcastChanges();
    }

    private static void purge(ServerPlayer player) {
        Enchantment ench = enchantment();
        if (ench != null) {
            Container inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                ItemStack clean = strip(stack, ench);
                if (clean != stack) inv.setItem(i, clean);
            }
        }
        removeModifier(player);
    }

    /**
     * Copia del objeto sin Life Boost, o el mismo objeto si no lo tenía. Un libro encantado que se queda sin
     * encantamientos pasa a ser un libro normal.
     */
    private static ItemStack strip(ItemStack stack, Enchantment ench) {
        if (stack.isEmpty()) return stack;
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        if (!enchants.containsKey(ench)) return stack;
        enchants.remove(ench);
        if (stack.is(Items.ENCHANTED_BOOK) && enchants.isEmpty()) {
            return new ItemStack(Items.BOOK, stack.getCount());
        }
        ItemStack copy = stack.copy();
        if (copy.is(Items.ENCHANTED_BOOK) && copy.getTag() != null) copy.getTag().remove("StoredEnchantments");
        EnchantmentHelper.setEnchantments(enchants, copy);
        return copy;
    }

    private static void removeModifier(Player player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null || health.getModifier(MODIFIER) == null) return;
        health.removeModifier(MODIFIER);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }
}
