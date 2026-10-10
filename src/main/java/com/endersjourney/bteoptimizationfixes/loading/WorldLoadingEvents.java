package com.endersjourney.bteoptimizationfixes.loading;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Marca el inicio y el fin del tramo "100% pero sigue cargando".
 *
 * Orden en 1.19.2 (IntegratedServer#initServer): ServerAboutToStartEvent ->
 * generacion de los chunks de spawn (la barra de 0 a 100%) ->
 * ServerStartingEvent -> ServerStartedEvent -> primer tick (aqui se crean
 * los reinos) -> el cliente se conecta. Por eso la fase empieza en
 * ServerStartingEvent, con prioridad HIGHEST para ir antes que la precarga
 * de estructuras de Beyond The End, que escucha ese mismo evento.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldLoadingEvents {

    private WorldLoadingEvents() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        WorldLoadingStatus.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStarting(ServerStartingEvent event) {
        WorldLoadingStatus.begin();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        WorldLoadingStatus.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WorldLoadingStatus.clear();
    }
}
