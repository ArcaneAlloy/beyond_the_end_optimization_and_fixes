package com.endersjourney.bteoptimizationfixes.compat.inventorytotem.client;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Inventory Totem (Natamus) hace que el Tótem de la inmortalidad funcione desde cualquier hueco del inventario, no solo
 * en la mano. Los jugadores no lo saben y siguen llevándolo en la mano secundaria, así que se avisa en el tooltip.
 *
 * Solo el tótem vanilla: Inventory Totem solo comprueba minecraft:totem_of_undying (los tótems de Dungeons Gear son
 * objetos que se colocan, no tótems de muerte). Sin Inventory Totem no se muestra nada.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TotemTooltip {

    private static Boolean inventoryTotemLoaded;

    private TotemTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(Items.TOTEM_OF_UNDYING)) return;
        if (inventoryTotemLoaded == null) inventoryTotemLoaded = ModList.get().isLoaded("inventorytotem");
        if (!inventoryTotemLoaded) return;
        // Justo debajo del nombre, antes de los datos avanzados (F3+H) que van al final
        int index = Math.min(1, event.getToolTip().size());
        event.getToolTip().add(index, Component.translatable("tooltip.bte_optimization_fixes.totem_inventory")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
