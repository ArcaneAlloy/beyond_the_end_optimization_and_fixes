package com.endersjourney.bteoptimizationfixes.language.client;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.LanguageSelectScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * La primera vez que el jugador abre la pantalla de idioma aparece un aviso (con el estilo de un tooltip) diciendo que,
 * por ahora, la traducción al español del modpack solo está en "Español (España)". El aviso va en inglés y en español a
 * la vez, porque se lee antes de elegir idioma. También dice que el modpack está traducido a English (US) y
 * Español (España).
 *
 *  - Se cierra con cualquier clic (ese clic no selecciona ningún idioma) o al salir de la pantalla.
 *  - Solo sale una vez por instalación: al mostrarlo se crea config/bte_optimization_fixes/language_notice_seen.
 *    Borrando ese archivo vuelve a salir.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LanguageNotice {

    private LanguageNotice() {
    }

    /** ¿Se está mostrando el aviso en la pantalla de idioma abierta ahora? */
    private static boolean showing = false;

    private static Path seenFile() {
        return FMLPaths.CONFIGDIR.get().resolve(BTEOptimizationFixes.MODID).resolve("language_notice_seen");
    }

    private static boolean alreadySeen() {
        try {
            return Files.exists(seenFile());
        } catch (Throwable t) {
            return true; // ante cualquier problema, mejor no molestar
        }
    }

    private static void markSeen() {
        try {
            Path file = seenFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, "The language notice was already shown. Delete this file to show it again.\n");
        } catch (Throwable t) {
            BTEOptimizationFixes.LOGGER.warn("[bteopt] No se pudo guardar que el aviso de idioma ya se mostro", t);
        }
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof LanguageSelectScreen)) return;
        // Init también se llama al cambiar el tamaño de la ventana: si ya se está mostrando, sigue
        if (showing || alreadySeen()) return;
        showing = true;
        markSeen();
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof LanguageSelectScreen) showing = false;
    }

    /** Cualquier clic cierra el aviso, y ese clic no llega a la pantalla (no cambia el idioma sin querer). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!showing || !(event.getScreen() instanceof LanguageSelectScreen)) return;
        showing = false;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!showing || !(event.getScreen() instanceof LanguageSelectScreen screen)) return;

        Font font = Minecraft.getInstance().font;
        int maxWidth = Math.min(screen.width - 40, 300);
        // Los textos en español usan escapes de Unicode para las tildes y la eñe: el código se compila con la codificación del sistema y en
        // Windows las letras con tilde y la eñe salían rotas.
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(font.split(Component.literal("Available languages").withStyle(ChatFormatting.GOLD), maxWidth));
        lines.addAll(font.split(Component.literal("For now, Ender's Journey is translated into ")
                .append(english()).append(" and ").append(spanish())
                .append(". Other Spanish variants are not translated yet."), maxWidth));
        lines.add(FormattedCharSequence.EMPTY);
        lines.addAll(font.split(Component.literal("Idiomas disponibles").withStyle(ChatFormatting.GOLD), maxWidth));
        lines.addAll(font.split(Component.literal("Por el momento, Ender's Journey est\u00e1 traducido a ")
                .append(english()).append(" y ").append(spanish())
                .append(". Las dem\u00e1s variantes del espa\u00f1ol a\u00fan no tienen traducci\u00f3n."), maxWidth));
        lines.add(FormattedCharSequence.EMPTY);
        lines.addAll(font.split(Component.literal("Click to close \u00b7 Haz clic para cerrar")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), maxWidth));

        int w = 0;
        for (FormattedCharSequence line : lines) w = Math.max(w, font.width(line));
        // Alto de la caja de renderTooltip: 8 px de la primera línea, 10 por cada una más y 2 de separación tras la
        // primera
        int h = 8 + (lines.size() - 1) * 10 + (lines.size() > 1 ? 2 : 0);
        // renderTooltip dibuja la caja 12 px a la derecha y 12 px por encima del punto dado: centrada en la pantalla
        int x = (screen.width - w) / 2 - 12;
        int y = (screen.height - h) / 2 + 12;
        screen.renderTooltip(event.getPoseStack(), lines, x, y);
    }

    private static MutableComponent english() {
        return Component.literal("English (US)").withStyle(ChatFormatting.YELLOW);
    }

    private static MutableComponent spanish() {
        return Component.literal("Espa\u00f1ol (Espa\u00f1a)").withStyle(ChatFormatting.YELLOW);
    }
}
