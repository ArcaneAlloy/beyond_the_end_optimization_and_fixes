package com.endersjourney.bteoptimizationfixes.compat.altarofsummoning.client;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

/**
 * Altar of Summoning: sus cuatro objetos de invocacion no dicen para que sirven. Se anade al tooltip el jefe de Meet
 * Your Fight que invoca cada uno y donde usarlo (clic derecho en el Altar of Summoning del Coliseo).
 *
 * El nombre del jefe es el de la entidad de Meet Your Fight (entity.meetyourfight.*), asi que sale traducido en el
 * idioma del juego y coincide con el nombre que se ve en la barra de vida.
 *
 * Objeto -> jefe, verificado en SummonProcedure del mod del altar:
 * hauntedbell -> bellringer, devilsante -> dame_fortuna, duskkey -> rosalyne, fossilbait -> swampjaw.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SummonItemTooltips {

    private static final String ALTAR_NAMESPACE = "altar_of_summoning";

    private static final Map<String, String> BOSS_BY_ITEM = Map.of(
            "hauntedbell", "bellringer",
            "devilsante", "dame_fortuna",
            "duskkey", "rosalyne",
            "fossilbait", "swampjaw");

    private SummonItemTooltips() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (id == null || !ALTAR_NAMESPACE.equals(id.getNamespace())) return;
        String boss = BOSS_BY_ITEM.get(id.getPath());
        if (boss == null) return;

        List<Component> tooltip = event.getToolTip();
        // Justo debajo del nombre, antes de los datos avanzados (F3+H) que van al final
        int index = Math.min(1, tooltip.size());
        tooltip.add(index, Component.translatable("tooltip.bte_optimization_fixes.altar_summon.boss",
                        Component.translatable("entity.meetyourfight." + boss).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(index + 1, Component.translatable("tooltip.bte_optimization_fixes.altar_summon.where")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
