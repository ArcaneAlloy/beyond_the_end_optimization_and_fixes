package com.endersjourney.bteoptimizationfixes.loading.client;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import com.endersjourney.bteoptimizationfixes.loading.WorldLoadingStatus;
import com.endersjourney.bteoptimizationfixes.loading.WorldLoadingStatus.Phase;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Dibuja, debajo del mapa de chunks de la pantalla de carga, que fase esta
 * preparando el modpack, unos puntos animados y los segundos transcurridos.
 * Lo que se mueve es lo que le demuestra al jugador que el juego no se ha
 * colgado: el hilo de render sigue vivo mientras el servidor trabaja.
 *
 * Usa ScreenEvent.Render.Post de Forge en vez de un Mixin sobre
 * LevelLoadingScreen, para no depender de nombres remapeados de Minecraft.
 *
 * Posicion: en 1.19.2 LevelLoadingScreen dibuja el mapa centrado en
 * (width/2, height/2 + 30) con 2 px por chunk y 45 chunks de diametro, es
 * decir, termina en height/2 + 75. El texto va justo debajo, partido en
 * varias lineas si la ventana es estrecha y subido si es muy baja.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldLoadingScreenOverlay {

    private static final int MAP_BOTTOM_OFFSET = 75;
    private static final int WHITE = 0xFFFFFF;
    private static final int GRAY = 0xA0A0A0;
    private static final int LINE_HEIGHT = 11;
    private static final int HINT_GAP = 3;
    private static final int SIDE_MARGIN = 6;

    private WorldLoadingScreenOverlay() {
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof LevelLoadingScreen)) {
            return;
        }
        Phase phase = WorldLoadingStatus.getPhase();
        if (phase == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null && server.isReady()) {
            // El servidor ya termino su primer tick; lo que queda es el
            // handshake de Forge y la entrada del jugador.
            phase = Phase.CONNECTING;
        }

        long now = System.currentTimeMillis();
        long seconds = Math.max(0L, (now - WorldLoadingStatus.getStartedAtMillis()) / 1000L);
        String dots = ".".repeat((int) ((now / 400L) % 4L));

        PoseStack poseStack = event.getPoseStack();
        Font font = minecraft.font;
        int centerX = screen.width / 2;
        int maxWidth = Math.max(80, screen.width - 2 * SIDE_MARGIN);

        // Linea 1: fase + segundos. Los puntos animados se dibujan aparte,
        // a la derecha, para que el texto centrado no "baile" al cambiar.
        Component title = Component.translatable(phase.translationKey()).append(" (" + seconds + " s)");
        List<FormattedCharSequence> titleLines = font.split(title, maxWidth);
        // Linea 2: aviso, partido en varias lineas si la ventana es estrecha.
        List<FormattedCharSequence> hintLines =
                font.split(Component.translatable("bte_optimization_fixes.loading.hint"), maxWidth);

        int totalHeight = (titleLines.size() + hintLines.size()) * LINE_HEIGHT + HINT_GAP;
        int y = screen.height / 2 + MAP_BOTTOM_OFFSET + 10;
        // Si no cabe debajo del mapa (ventana muy baja), se sube lo justo.
        y = Math.min(y, screen.height - totalHeight - SIDE_MARGIN);

        for (int i = 0; i < titleLines.size(); i++) {
            FormattedCharSequence line = titleLines.get(i);
            GuiComponent.drawCenteredString(poseStack, font, line, centerX, y, WHITE);
            if (i == titleLines.size() - 1) {
                int lineRight = centerX - font.width(line) / 2 + font.width(line);
                GuiComponent.drawString(poseStack, font, dots, lineRight, y, WHITE);
            }
            y += LINE_HEIGHT;
        }
        y += HINT_GAP;
        for (FormattedCharSequence line : hintLines) {
            GuiComponent.drawCenteredString(poseStack, font, line, centerX, y, GRAY);
            y += LINE_HEIGHT;
        }
    }
}
