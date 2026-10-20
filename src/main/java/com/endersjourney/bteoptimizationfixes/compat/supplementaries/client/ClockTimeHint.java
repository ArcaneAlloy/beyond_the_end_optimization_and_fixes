package com.endersjourney.bteoptimizationfixes.compat.supplementaries.client;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Al mirar un reloj de Supplementaries aparece, encima de la barra de vida, un aviso con el mismo estilo que el del
 * Altar de Invocación: el icono del reloj de Minecraft marcando la hora actual, la hora (formato 24 h), si es de día,
 * de noche, está amaneciendo o atardeciendo, y una segunda línea indicando que es la hora del Overworld.
 *
 * Todas las dimensiones comparten la hora del Overworld, así que Level#getDayTime() del cliente sirve en cualquiera.
 * Sin Supplementaries el bloque no existe y no se muestra nada.
 */
public final class ClockTimeHint {

    private static final ResourceLocation CLOCK_BLOCK = new ResourceLocation("supplementaries", "clock_block");

    /** Opacidad del aviso (0..1), para que aparezca y desaparezca suavemente. */
    private static float alpha = 0F;

    private ClockTimeHint() {
    }

    private enum Phase {
        SUNRISE("sunrise"),
        DAY("day"),
        SUNSET("sunset"),
        NIGHT("night");

        final String key;

        Phase(String key) {
            this.key = key;
        }

        /**
         * Tramos en ticks del día (0 = 06:00): amanecer 23000-1000 (05:00-07:00), día 1000-12000 (07:00-18:00),
         * atardecer 12000-13000 (18:00-19:00, el sol se pone) y noche 13000-23000 (19:00-05:00, salen monstruos).
         */
        static Phase of(int ticks) {
            if (ticks >= 23000 || ticks < 1000) return SUNRISE;
            if (ticks < 12000) return DAY;
            if (ticks < 13000) return SUNSET;
            return NIGHT;
        }
    }

    private static final ResourceLocation[] CLOCK_FRAMES = new ResourceLocation[64];

    static {
        for (int i = 0; i < 64; i++) {
            CLOCK_FRAMES[i] = new ResourceLocation("minecraft", String.format(java.util.Locale.ROOT, "textures/item/clock_%02d.png", i));
        }
    }

    /**
     * Fotograma del reloj vanilla para esa hora. Misma fórmula que DimensionType#timeOfDay (sin tiempo fijo, porque el
     * reloj de item vanilla gira al azar fuera del Overworld) y que los overrides "time" de models/item/clock.json.
     */
    private static ResourceLocation clockFrame(long dayTime) {
        double d = net.minecraft.util.Mth.frac(dayTime / 24000.0D - 0.25D);
        double e = 0.5D - Math.cos(d * Math.PI) / 2.0D;
        double time = (d * 2.0D + e) / 3.0D;
        int frame = (int) Math.round(time * 64.0D) & 63;
        return CLOCK_FRAMES[frame];
    }

    private static boolean lookingAtClock(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return false;
        Block block = mc.level.getBlockState(blockHit.getBlockPos()).getBlock();
        return CLOCK_BLOCK.equals(ForgeRegistries.BLOCKS.getKey(block));
    }

    @Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            boolean show = mc.screen == null && lookingAtClock(mc);
            alpha = show ? Math.min(1F, alpha + 0.2F) : Math.max(0F, alpha - 0.2F);
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            if (alpha <= 0.02F) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui || mc.screen != null || mc.level == null) return;

            long dayTime = mc.level.getDayTime();
            int ticks = (int) (dayTime % 24000L);
            int hour = (ticks / 1000 + 6) % 24;
            int minute = (ticks % 1000) * 60 / 1000;
            Phase phase = Phase.of(ticks);

            Component line1 = Component.literal(String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute))
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("  \u00B7  ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("bte_optimization_fixes.clock." + phase.key).withStyle(ChatFormatting.WHITE));
            Component line2 = Component.translatable("bte_optimization_fixes.clock.overworld").withStyle(ChatFormatting.GRAY);

            PoseStack stack = event.getPoseStack();
            Font font = mc.font;
            int icon = 16, gap = 5;
            int textW = Math.max(font.width(line1), font.width(line2));
            int w = icon + gap + textW;
            int cx = event.getWindow().getGuiScaledWidth() / 2;
            // Mismo hueco que el aviso del altar: encima de la barra de vida/armadura. Dos líneas, así que sube una.
            int y = event.getWindow().getGuiScaledHeight() - 72 - (font.lineHeight + 2);
            int padX = 6, padY = 4;
            int textH = font.lineHeight * 2 + 2;

            int bgAlpha = (int) (alpha * 0x88);
            int borderAlpha = (int) (alpha * 0x55);
            int textAlpha = Math.max(4, (int) (alpha * 0xFF));
            int x0 = cx - w / 2 - padX, x1 = cx + w / 2 + padX;
            int y0 = y - padY, y1 = y + textH + padY - 1;
            int textX = x0 + padX + icon + gap;

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            GuiComponent.fill(stack, x0, y0, x1, y1, bgAlpha << 24);
            GuiComponent.fill(stack, x0, y0, x1, y0 + 1, (borderAlpha << 24) | 0xB070FF);
            GuiComponent.fill(stack, x0, y1 - 1, x1, y1, (borderAlpha << 24) | 0xB070FF);

            // Icono del reloj de Minecraft con el fotograma que corresponde a la hora del Overworld
            RenderSystem.setShaderTexture(0, clockFrame(dayTime));
            RenderSystem.setShaderColor(1F, 1F, 1F, alpha);
            GuiComponent.blit(stack, x0 + padX, y + (textH - icon) / 2, 0, 0, icon, icon, icon, icon);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

            font.drawShadow(stack, line1, textX, y, (textAlpha << 24) | 0xFFFFFF);
            font.drawShadow(stack, line2, textX, y + font.lineHeight + 2, (textAlpha << 24) | 0xFFFFFF);
            RenderSystem.disableBlend();
        }
    }
}
