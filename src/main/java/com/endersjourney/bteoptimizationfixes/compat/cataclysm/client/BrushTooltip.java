package com.endersjourney.bteoptimizationfixes.compat.cataclysm.client;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * L_Ender's Cataclysm 2.46 trae a la 1.19.2 la arqueologia de la 1.20 y registra el pincel como minecraft:brush. En el
 * pack solo sirve para desenterrar la arena sospechosa de la Cursed Pyramid (y despertar al Ancient Remnant), y no dice
 * nada de eso: se anade al tooltip. Sin Cataclysm no se muestra nada.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BrushTooltip {

    private static final ResourceLocation BRUSH = new ResourceLocation("minecraft", "brush");

    private static Boolean cataclysmLoaded;

    private BrushTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!BRUSH.equals(ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem()))) return;
        if (cataclysmLoaded == null) cataclysmLoaded = ModList.get().isLoaded("cataclysm");
        if (!cataclysmLoaded) return;
        // Justo debajo del nombre, antes de los datos avanzados (F3+H) que van al final
        int index = Math.min(1, event.getToolTip().size());
        event.getToolTip().add(index, Component.translatable("tooltip.bte_optimization_fixes.brush_cursed_pyramid")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
