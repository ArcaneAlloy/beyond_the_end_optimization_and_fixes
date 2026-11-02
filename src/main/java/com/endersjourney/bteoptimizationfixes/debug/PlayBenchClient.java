package com.endersjourney.bteoptimizationfixes.debug;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mide el tiempo entre frames del cliente mientras playbench está midiendo una fase (no hace nada el resto del
 * tiempo). Solo cliente.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayBenchClient {

    private PlayBenchClient() {
    }

    private static long lastFrame;

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!PlayBenchFrames.recording) {
            lastFrame = 0;
            return;
        }
        long now = System.nanoTime();
        if (lastFrame == 0) {
            Minecraft mc = Minecraft.getInstance();
            PlayBenchFrames.clientInfo = "fps_max " + mc.options.framerateLimit().get()
                    + ", vsync " + mc.options.enableVsync().get()
                    + ", render " + mc.options.renderDistance().get()
                    + ", simulacion " + mc.options.simulationDistance().get()
                    + ", gui " + mc.options.guiScale().get()
                    + ", ventana " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight();
        } else {
            PlayBenchFrames.frame(now - lastFrame);
        }
        lastFrame = now;
    }
}
