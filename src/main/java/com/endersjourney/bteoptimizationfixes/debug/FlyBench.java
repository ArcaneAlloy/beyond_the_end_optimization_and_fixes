package com.endersjourney.bteoptimizationfixes.debug;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Prueba de "jugador volando" (herramienta de depuracion, no hace nada si no se lanza).
 *
 *   /bteopt flybench <dimension> [bloques/s] [distancia]     p. ej. /bteopt flybench minecraft:overworld 20 3000
 *   /bteopt flybench stop
 *
 * Pone al jugador en espectador y lo mueve en linea recta hacia +X desde (0, Y, 0) a velocidad constante POR TICK
 * (como un jugador volando: si el servidor va con lag, tarda mas en tiempo real). Y = 200 en el Overworld y 100 en
 * el resto. Durante la prueba mide:
 *  - el tiempo de cada tick del servidor (media, p50, p95, p99, maximo, ticks de mas de 50 ms);
 *  - los chunks sin cargar (FULL) dentro de la distancia de vision alrededor del jugador, en total y en la mitad de
 *    delante (los que el jugador ve llegar);
 *  - si el chunk donde esta el jugador no esta cargado (en una partida real eso es quedarse congelado);
 *  - el tiempo real que tarda en recorrer la ruta frente al ideal.
 * Ademas lanza el perfilador de generacion (genprof) durante toda la prueba, marcando cada muestra con el tramo de
 * 250 bloques en que esta el jugador ("[tramo x=2500]"). Al terminar escribe
 * logs/bteopt_flybench_<dimension>_<fecha>.txt (resumen + linea de tiempo por segundo) y el fichero de genprof, y
 * devuelve al jugador a su modo de juego.
 *
 * Para comparar ejecuciones: mundo nuevo con la misma semilla, mismos ajustes y misma distancia de vision.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FlyBench {

    private FlyBench() {
    }

    private static final class Run {
        ServerPlayer player;
        ServerLevel level;
        GameType previousMode;
        double y, speedPerTick;
        int distance;
        int tick;
        long startNanos, lastSecondNanos;
        final List<Long> tickNanos = new ArrayList<>();
        long missingSum, aheadMissingSum, samples;
        int missingMax, aheadMissingMax, standingUnloadedTicks, radius;
        // linea de tiempo por segundo real
        final StringBuilder timeline = new StringBuilder();
        long secTickNanos, secTicks, secMissing, secAhead, secMaxTick;
    }

    private static Run run;
    private static long tickStart;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("bteopt")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("flybench")
                        .then(Commands.literal("stop").executes(c -> stop(c.getSource().getServer(), "detenida a mano")))
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(c -> start(c, 20, 3000))
                                .then(Commands.argument("speed", IntegerArgumentType.integer(1, 200))
                                        .executes(c -> start(c, IntegerArgumentType.getInteger(c, "speed"), 3000))
                                        .then(Commands.argument("distance", IntegerArgumentType.integer(16, 100000))
                                                .executes(c -> start(c, IntegerArgumentType.getInteger(c, "speed"),
                                                        IntegerArgumentType.getInteger(c, "distance"))))))));
    }

    private static int start(CommandContext<CommandSourceStack> c, int speed, int distance) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (run != null) {
            c.getSource().sendFailure(Component.literal("[flybench] Ya hay una prueba en marcha (/bteopt flybench stop)"));
            return 0;
        }
        ServerPlayer player = c.getSource().getPlayerOrException();
        ServerLevel level = DimensionArgument.getDimension(c, "dimension");

        Run r = new Run();
        r.player = player;
        r.level = level;
        r.previousMode = player.gameMode.getGameModeForPlayer();
        r.y = level.dimension() == Level.OVERWORLD ? 200 : 100;
        r.speedPerTick = speed / 20.0;
        r.distance = distance;
        r.radius = Math.min(level.getServer().getPlayerList().getViewDistance(), 12);

        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(level, 0.5, r.y, 0.5, -90f, 0f); // yaw -90 = mirando a +X
        run = r;
        r.startNanos = r.lastSecondNanos = System.nanoTime();
        WorldgenProfiler.start();

        String msg = "[flybench] " + level.dimension().location() + ": " + speed + " bloques/s, " + distance
                + " bloques, radio " + r.radius + " chunks";
        c.getSource().sendSuccess(Component.literal(msg), false);
        BTEOptimizationFixes.LOGGER.info(msg);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tickStart = System.nanoTime();
            return;
        }
        Run r = run;
        if (r == null) return;
        long now = System.nanoTime();
        long dt = now - tickStart;
        r.tickNanos.add(dt);
        r.secTickNanos += dt;
        r.secTicks++;
        r.secMaxTick = Math.max(r.secMaxTick, dt);

        if (r.player.isRemoved() || r.player.hasDisconnected()) {
            stop(r.level.getServer(), "el jugador se ha ido");
            return;
        }

        // Medir alrededor de la posicion actual (antes de mover)
        int pcx = r.player.getBlockX() >> 4, pcz = r.player.getBlockZ() >> 4;
        if (r.player.level == r.level) {
            int missing = 0, ahead = 0;
            for (int dx = -r.radius; dx <= r.radius; dx++) {
                for (int dz = -r.radius; dz <= r.radius; dz++) {
                    if (r.level.getChunkSource().getChunkNow(pcx + dx, pcz + dz) == null) {
                        missing++;
                        if (dx > 0) ahead++;
                    }
                }
            }
            if (r.level.getChunkSource().getChunkNow(pcx, pcz) == null) r.standingUnloadedTicks++;
            r.missingSum += missing;
            r.aheadMissingSum += ahead;
            r.samples++;
            r.missingMax = Math.max(r.missingMax, missing);
            r.aheadMissingMax = Math.max(r.aheadMissingMax, ahead);
            r.secMissing += missing;
            r.secAhead += ahead;
        }

        if (now - r.lastSecondNanos >= 1_000_000_000L) {
            double t = (now - r.startNanos) / 1e9;
            r.timeline.append(String.format("%6.1f s  x=%6d  ticks=%3d  mspt_media=%6.1f  mspt_max=%6.1f  sin_cargar=%6.1f  delante=%6.1f%n",
                    t, r.player.getBlockX(), r.secTicks, r.secTickNanos / 1e6 / Math.max(1, r.secTicks),
                    r.secMaxTick / 1e6, (double) r.secMissing / Math.max(1, r.secTicks), (double) r.secAhead / Math.max(1, r.secTicks)));
            r.lastSecondNanos = now;
            r.secTickNanos = r.secTicks = r.secMissing = r.secAhead = r.secMaxTick = 0;
        }

        // Mover
        r.tick++;
        double x = 0.5 + r.tick * r.speedPerTick;
        if (x >= r.distance) {
            stop(r.level.getServer(), "completada");
            return;
        }
        WorldgenProfiler.segment = "[tramo x=" + ((int) x / 250 * 250) + "]";
        if (r.player.level != r.level) {
            r.player.teleportTo(r.level, x, r.y, 0.5, -90f, 0f);
        } else {
            r.player.connection.teleport(x, r.y, 0.5, -90f, 0f);
        }
    }

    private static int stop(MinecraftServer server, String why) {
        Run r = run;
        if (r == null) return 0;
        run = null;
        WorldgenProfiler.stopSampler();
        WorldgenProfiler.segment = null;
        Path prof = null;
        try {
            prof = WorldgenProfiler.write();
        } catch (IOException e) {
            BTEOptimizationFixes.LOGGER.warn("[flybench] No se pudo escribir genprof", e);
        }

        double real = (System.nanoTime() - r.startNanos) / 1e9;
        double ideal = r.tick / 20.0;
        long[] t = r.tickNanos.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(t);
        double mean = Arrays.stream(t).average().orElse(0) / 1e6;
        long over50 = Arrays.stream(t).filter(v -> v > 50_000_000L).count();
        String summary = String.format(
                "[flybench] %s (%s): %d bloques en %.1f s reales (ideal %.1f s, %.0f%%) | MSPT media %.1f p50 %.1f p95 %.1f p99 %.1f max %.1f | ticks >50ms: %d de %d | sin cargar (radio %d): media %.1f max %d | delante: media %.1f max %d | ticks con el jugador en chunk sin cargar: %d",
                r.level.dimension().location(), why, (int) (r.tick * r.speedPerTick), real, ideal, 100 * ideal / Math.max(real, 1e-9),
                mean, pct(t, 50), pct(t, 95), pct(t, 99), t.length == 0 ? 0 : t[t.length - 1] / 1e6, over50, t.length,
                r.radius, (double) r.missingSum / Math.max(1, r.samples), r.missingMax,
                (double) r.aheadMissingSum / Math.max(1, r.samples), r.aheadMissingMax, r.standingUnloadedTicks);
        BTEOptimizationFixes.LOGGER.info(summary);

        try {
            String dim = r.level.dimension().location().toString().replace(':', '_');
            Path file = FMLPaths.GAMEDIR.get().resolve("logs").resolve("bteopt_flybench_" + dim + "_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(summary + System.lineSeparator());
                w.write("seed " + server.getWorldData().worldGenSettings().seed() + ", distancia de vision "
                        + server.getPlayerList().getViewDistance() + ", genprof: " + (prof == null ? "-" : prof.getFileName())
                        + System.lineSeparator() + System.lineSeparator());
                w.write(r.timeline.toString());
            }
            BTEOptimizationFixes.LOGGER.info("[flybench] Informe en logs/{}", file.getFileName());
        } catch (IOException e) {
            BTEOptimizationFixes.LOGGER.warn("[flybench] No se pudo escribir el informe", e);
        }

        if (!r.player.isRemoved()) {
            r.player.setGameMode(r.previousMode);
            r.player.sendSystemMessage(Component.literal(summary));
        }
        return 1;
    }

    private static double pct(long[] sorted, int p) {
        if (sorted.length == 0) return 0;
        int i = (int) Math.min(sorted.length - 1, Math.ceil(p / 100.0 * sorted.length) - 1);
        return sorted[Math.max(0, i)] / 1e6;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (run != null) stop(event.getServer(), "servidor parado");
    }
}
