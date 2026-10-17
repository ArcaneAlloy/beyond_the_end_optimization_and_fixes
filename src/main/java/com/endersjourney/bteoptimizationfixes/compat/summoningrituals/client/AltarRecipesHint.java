package com.endersjourney.bteoptimizationfixes.compat.summoningrituals.client;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

/**
 * Al mirar un altar indestructible de Summoning Rituals aparece, encima de la barra de vida, un aviso discreto y semitransparente:
 * "Pulsa U para ver todas las recetas del Altar de Invocación". Con la tecla (U por defecto, configurable en
 * Controles) se abre JEI con todas las recetas del altar.
 *
 * Sin Summoning Rituals el bloque no existe y no se muestra nada; sin JEI se muestra el aviso pero la tecla no hace
 * nada (por eso el aviso solo sale si JEI está cargado).
 */
public final class AltarRecipesHint {

    private static final ResourceLocation INDESTRUCTIBLE_ALTAR = new ResourceLocation("summoningrituals", "indestructible_altar");

    public static final KeyMapping KEY = new KeyMapping("key.bte_optimization_fixes.altar_recipes",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U,
            "key.categories.bte_optimization_fixes");

    /** Opacidad del aviso (0..1), para que aparezca y desaparezca suavemente. */
    private static float alpha = 0F;

    private AltarRecipesHint() {
    }

    private static boolean jeiLoaded() {
        return ModList.get().isLoaded("jei");
    }

    /** ¿El jugador está mirando un altar indestructible? */
    private static boolean lookingAtAltar(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return false;
        Block block = mc.level.getBlockState(blockHit.getBlockPos()).getBlock();
        return INDESTRUCTIBLE_ALTAR.equals(ForgeRegistries.BLOCKS.getKey(block));
    }

    @Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            boolean show = mc.screen == null && jeiLoaded() && lookingAtAltar(mc);
            alpha = show ? Math.min(1F, alpha + 0.2F) : Math.max(0F, alpha - 0.2F);

            while (KEY.consumeClick()) {
                if (show) JeiAltarRecipes.show();
            }
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            if (alpha <= 0.02F) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui || mc.screen != null) return;

            PoseStack stack = event.getPoseStack();
            Font font = mc.font;
            // Teclas de una letra en mayúscula ("U" en vez de "u")
            String keyName = KEY.getTranslatedKeyMessage().getString();
            if (keyName.length() == 1) keyName = keyName.toUpperCase(java.util.Locale.ROOT);
            Component text = Component.translatable("bte_optimization_fixes.altar.hint",
                    Component.literal(keyName).withStyle(net.minecraft.ChatFormatting.YELLOW));
            int w = font.width(text);
            int cx = event.getWindow().getGuiScaledWidth() / 2;
            // Encima de la barra de vida/armadura (que empieza unos 49 px por encima del borde inferior)
            int y = event.getWindow().getGuiScaledHeight() - 72;
            int padX = 6, padY = 4;

            int bgAlpha = (int) (alpha * 0x88);
            int borderAlpha = (int) (alpha * 0x55);
            int textAlpha = Math.max(4, (int) (alpha * 0xFF));
            int x0 = cx - w / 2 - padX, x1 = cx + w / 2 + padX;
            int y0 = y - padY, y1 = y + font.lineHeight + padY - 1;

            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            GuiComponent.fill(stack, x0, y0, x1, y1, bgAlpha << 24);
            GuiComponent.fill(stack, x0, y0, x1, y0 + 1, (borderAlpha << 24) | 0xB070FF);
            GuiComponent.fill(stack, x0, y1 - 1, x1, y1, (borderAlpha << 24) | 0xB070FF);
            font.drawShadow(stack, text, cx - w / 2F, y, (textAlpha << 24) | 0xFFFFFF);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        }
    }
}
