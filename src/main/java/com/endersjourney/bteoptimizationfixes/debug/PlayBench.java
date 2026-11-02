package com.endersjourney.bteoptimizationfixes.debug;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Writer;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Prueba de partida automática y repetible (herramienta de depuración, no hace nada si no se lanza).
 *
 *   /bteopt playbench [segundos_por_fase]     p. ej. /bteopt playbench 60
 *   /bteopt playbench stop
 *
 * Pensada para un mundo NUEVO con la misma semilla en cada ejecución: se lanza nada más aparecer en el Lobby. Pasa
 * por 5 fases, siempre en los mismos sitios y en las mismas condiciones, con el jugador quieto y mirando siempre
 * hacia el mismo lado (supervivencia, con Resistencia V y saturación para que los mobs le ataquen pero no le maten):
 *
 *   1. lobby   donde está el jugador al lanzar el comando (el Lobby).
 *   2. dia     Overworld, primer punto de tierra firme desde (1000, 1000), a mediodía.
 *   3. noche   el mismo punto, a medianoche, con aparición natural de mobs.
 *   4. horda   el mismo punto, sin aparición natural: se quitan los mobs cercanos y se invoca una horda fija
 *              (config/bteopt_playbench_mobs.txt, "id cantidad" por línea; se crea con una lista por defecto).
 *   5. nether  Nether, primer suelo firme desde (300, 300).
 *
 * Cada fase espera primero a que estén cargados los chunks alrededor del jugador (máximo 60 s) y 10 s más para que
 * se asiente, y luego mide durante los segundos indicados:
 *  - servidor: tiempo de cada tick (media, p50, p95, p99, máximo, ticks de más de 50 ms);
 *  - cliente (solo un jugador): tiempo entre frames y FPS (media, p95, p99, "1% low");
 *  - recolector de basura: número de pasadas y tiempo total; memoria usada máxima;
 *  - entidades cargadas en la dimensión y mobs a menos de 64 bloques (media y máximo), chunks cargados.
 * Durante toda la prueba toma muestras de los hilos del servidor, de generación y de render (como genprof), cada
 * muestra marcada con su fase ("[fase noche]"; "[preparando]" mientras espera).
 *
 * Al terminar escribe logs/bteopt_playbench_<fecha>.txt y el fichero de muestras, y devuelve todo como estaba
 * (reglas de juego, hora, modo de juego, posición; quita los efectos y la horda).
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayBench {

    private PlayBench() {
    }

    private static final int PREPARE_MAX_TICKS = 60 * 20;
    private static final int SETTLE_TICKS = 10 * 20;
    private static final int HORDE_RADIUS = 12;
    private static final String[] DEFAULT_HORDE = {
            "# Horda de la fase 'horda' de /bteopt playbench: \"id cantidad\" por linea. Los ids que no existan se ignoran.",
            "minecraft:zombie 6", "minecraft:skeleton 6", "minecraft:spider 4", "minecraft:creeper 3",
            "minecraft:enderman 2", "minecraft:witch 2", "minecraft:pillager 3", "minecraft:vindicator 2",
            "alexsmobs:crocodile 2", "alexsmobs:gorilla 2", "alexsmobs:rattlesnake 2", "alexsmobs:grizzly_bear 2",
            "alexsmobs:cockroach 4", "alexsmobs:emu 2"
    };

    private enum Phase { LOBBY, DIA, NOCHE, HORDA, NETHER }

    private enum State { PREPARE, SETTLE, MEASURE }

    private static final class Run {
        ServerPlayer player;
        MinecraftServer server;
        int measureTicks;
        // estado original
        GameType previousMode;
        boolean previousInvulnerable;
        ResourceKey<Level> startDim;
        Vec3 startPos;
        float startYaw, startPitch;
        boolean ruleDaylight, ruleSpawning, ruleWeather, ruleGriefing;
        long dayTime;
        // fase en curso
        int phaseIndex = -1;
        State state;
        int stateTicks;
        ServerLevel level;
        Vec3 pos;
        final List<Entity> horde = new ArrayList<>();
        String hordeInfo = "-";
        // medidas de la fase
        final List<Long> tickNanos = new ArrayList<>();
        long gcCount0, gcTime0, maxHeap;
        long entitySum, entityMax, mobSum, mobMax, chunkSum, samples;
        int prepareTicks;
        final StringBuilder report = new StringBuilder();
    }

    private static Run run;
    private static long tickStart;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("bteopt")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("playbench")
                        .executes(c -> start(c, 60))
                        .then(Commands.literal("stop").executes(c -> finish("detenida a mano")))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 600))
                                .executes(c -> start(c, IntegerArgumentType.getInteger(c, "seconds"))))));
    }

    private static int start(CommandContext<CommandSourceStack> c, int seconds) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (run != null) {
            c.getSource().sendFailure(Component.literal("[playbench] Ya hay una prueba en marcha (/bteopt playbench stop)"));
            return 0;
        }
        ServerPlayer player = c.getSource().getPlayerOrException();
        MinecraftServer server = c.getSource().getServer();
        Run r = new Run();
        r.player = player;
        r.server = server;
        r.measureTicks = seconds * 20;
        r.previousMode = player.gameMode.getGameModeForPlayer();
        r.startDim = player.level.dimension();
        r.startPos = player.position();
        r.startYaw = player.getYRot();
        r.startPitch = player.getXRot();
        GameRules rules = server.getGameRules();
        r.ruleDaylight = rules.getRule(GameRules.RULE_DAYLIGHT).get();
        r.ruleSpawning = rules.getRule(GameRules.RULE_DOMOBSPAWNING).get();
        r.ruleWeather = rules.getRule(GameRules.RULE_WEATHER_CYCLE).get();
        r.ruleGriefing = rules.getRule(GameRules.RULE_MOBGRIEFING).get();
        r.dayTime = server.overworld().getDayTime();

        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        // Sin destrozos (creepers, endermans...): el terreno es el mismo en cada fase y en cada ejecucion
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(false, server);
        server.overworld().setWeatherParameters(1_000_000, 0, false, false);
        player.setGameMode(GameType.SURVIVAL);
        applyEffects(player);
        // Invulnerable: los mobs le siguen viendo y atacando (en supervivencia), pero nada le hace daño. Solo con
        // Resistencia V una explosión de creeper lo mató y cortó la prueba.
        r.previousInvulnerable = player.getAbilities().invulnerable;
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();

        run = r;
        WorldgenProfiler.includeRender = true;
        WorldgenProfiler.segment = "[preparando]";
        WorldgenProfiler.start();
        String msg = "[playbench] Empezando: 5 fases de " + seconds + " s (lobby, dia, noche, horda, nether). No pauses el juego.";
        c.getSource().sendSuccess(Component.literal(msg), false);
        BTEOptimizationFixes.LOGGER.info(msg);
        nextPhase(r);
        return 1;
    }

    private static void applyEffects(ServerPlayer p) {
        int t = 60 * 60 * 20;
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, t, 4, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, t, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, t, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.SATURATION, t, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, t, 2, false, false));
    }

    /** Vuelve a poner los efectos de la prueba que falten o que vayan a acabarse, o que alguien haya cambiado. */
    private static void maintainEffects(ServerPlayer p) {
        int t = 60 * 60 * 20;
        int[] amps = {4, 0, 0, 0, 2};
        for (int i = 0; i < EFFECTS.length; i++) {
            MobEffectInstance cur = p.getEffect(EFFECTS[i]);
            if (cur == null || cur.getAmplifier() < amps[i] || cur.getDuration() < 20 * 20) {
                p.removeEffect(EFFECTS[i]);
                p.addEffect(new MobEffectInstance(EFFECTS[i], t, amps[i], false, false));
            }
        }
        if (p.getHealth() < p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    private static final MobEffect[] EFFECTS = {MobEffects.DAMAGE_RESISTANCE, MobEffects.FIRE_RESISTANCE,
            MobEffects.WATER_BREATHING, MobEffects.SATURATION, MobEffects.REGENERATION};

    private static void nextPhase(Run r) {
        r.phaseIndex++;
        if (r.phaseIndex >= Phase.values().length) {
            finish("completada");
            return;
        }
        Phase phase = Phase.values()[r.phaseIndex];
        MinecraftServer server = r.server;
        GameRules rules = server.getGameRules();
        WorldgenProfiler.segment = "[preparando]";
        switch (phase) {
            case LOBBY -> {
                r.level = server.getLevel(r.startDim);
                r.pos = r.startPos;
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(r.ruleSpawning, server);
            }
            case DIA, NOCHE, HORDA -> {
                r.level = server.overworld();
                if (phase == Phase.DIA) r.pos = findOverworldSpot(r.level, 1000, 1000);
                r.level.setDayTime(phase == Phase.DIA ? 6000 : 18000);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(phase != Phase.HORDA, server);
            }
            case NETHER -> {
                clearHorde(r);
                r.level = server.getLevel(Level.NETHER);
                r.pos = findNetherSpot(r.level, 300, 300);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(true, server);
            }
        }
        r.player.teleportTo(r.level, r.pos.x, r.pos.y, r.pos.z, 0f, 0f);
        r.state = State.PREPARE;
        r.stateTicks = 0;
        String msg = "[playbench] Fase " + (r.phaseIndex + 1) + "/5: " + phase.name().toLowerCase() + " en "
                + r.level.dimension().location() + " " + new BlockPos(r.pos).toShortString();
        r.player.sendSystemMessage(Component.literal(msg));
        BTEOptimizationFixes.LOGGER.info(msg);
    }

    /** Primer punto de tierra firme (no agua ni lava) en espiral desde (x, z), siempre el mismo para la misma semilla. */
    private static Vec3 findOverworldSpot(ServerLevel level, int x0, int z0) {
        for (int ring = 0; ring <= 32; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int[] o : new int[][]{{i, -ring}, {i, ring}, {-ring, i}, {ring, i}}) {
                    if (ring > 0 && Math.abs(o[0]) != ring && Math.abs(o[1]) != ring) continue;
                    int x = x0 + o[0] * 16, z = z0 + o[1] * 16;
                    level.getChunk(x >> 4, z >> 4);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockState below = level.getBlockState(new BlockPos(x, y - 1, z));
                    if (below.getFluidState().isEmpty() && y > level.getSeaLevel()) return new Vec3(x + 0.5, y, z + 0.5);
                }
            }
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
        return new Vec3(x0 + 0.5, y, z0 + 0.5);
    }

    /** Primer suelo firme con 2 bloques de aire encima (de y=100 hacia abajo), en espiral desde (x, z). */
    private static Vec3 findNetherSpot(ServerLevel level, int x0, int z0) {
        for (int ring = 0; ring <= 32; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int[] o : new int[][]{{i, -ring}, {i, ring}, {-ring, i}, {ring, i}}) {
                    if (ring > 0 && Math.abs(o[0]) != ring && Math.abs(o[1]) != ring) continue;
                    int x = x0 + o[0] * 16, z = z0 + o[1] * 16;
                    level.getChunk(x >> 4, z >> 4);
                    for (int y = 100; y > 33; y--) {
                        BlockPos p = new BlockPos(x, y, z);
                        BlockState floor = level.getBlockState(p.below());
                        if (floor.getFluidState().isEmpty() && floor.isFaceSturdy(level, p.below(), Direction.UP)
                                && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) {
                            return new Vec3(x + 0.5, y, z + 0.5);
                        }
                    }
                }
            }
        }
        return new Vec3(x0 + 0.5, 70, z0 + 0.5);
    }

    private static void spawnHorde(Run r) {
        // Fuera los mobs que hubiera de la fase de noche: la horda es siempre la misma
        AABB box = new AABB(r.pos, r.pos).inflate(64);
        for (Mob m : r.level.getEntitiesOfClass(Mob.class, box)) {
            if (!m.isPersistenceRequired()) m.discard();
        }
        List<String> lines;
        Path cfg = FMLPaths.CONFIGDIR.get().resolve("bteopt_playbench_mobs.txt");
        try {
            if (!Files.exists(cfg)) Files.write(cfg, Arrays.asList(DEFAULT_HORDE), StandardCharsets.UTF_8);
            lines = Files.readAllLines(cfg, StandardCharsets.UTF_8);
        } catch (IOException e) {
            lines = Arrays.asList(DEFAULT_HORDE);
        }
        List<String[]> entries = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            if (parts.length > 1) {
                try {
                    Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
            }
            entries.add(parts);
        }
        int total = 0;
        for (String[] e : entries) total += e.length > 1 ? Integer.parseInt(e[1]) : 1;
        StringBuilder info = new StringBuilder();
        int index = 0;
        for (String[] e : entries) {
            int count = e.length > 1 ? Integer.parseInt(e[1]) : 1;
            EntityType<?> type = EntityType.byString(e[0]).orElse(null);
            int spawned = 0;
            for (int k = 0; k < count; k++, index++) {
                if (type == null) continue;
                double angle = 2 * Math.PI * index / Math.max(1, total);
                double x = r.pos.x + Math.cos(angle) * HORDE_RADIUS, z = r.pos.z + Math.sin(angle) * HORDE_RADIUS;
                int y = r.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
                Entity ent = type.create(r.level);
                if (ent == null) continue;
                ent.moveTo(x, y, z, (float) Math.toDegrees(angle) + 90f, 0f);
                if (ent instanceof Mob mob) {
                    mob.finalizeSpawn(r.level, r.level.getCurrentDifficultyAt(ent.blockPosition()), MobSpawnType.COMMAND, null, null);
                }
                if (r.level.addFreshEntity(ent)) {
                    r.horde.add(ent);
                    spawned++;
                }
            }
            if (info.length() > 0) info.append(", ");
            info.append(e[0]).append(type == null ? " (no existe)" : " x" + spawned);
        }
        r.hordeInfo = info.toString();
    }

    private static void clearHorde(Run r) {
        for (Entity e : r.horde) if (!e.isRemoved()) e.discard();
        r.horde.clear();
    }

    private static long[] gcTotals() {
        long count = 0, time = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(0, gc.getCollectionCount());
            time += Math.max(0, gc.getCollectionTime());
        }
        return new long[]{count, time};
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tickStart = System.nanoTime();
            return;
        }
        Run r = run;
        if (r == null) return;
        long dt = System.nanoTime() - tickStart;
        if (r.player.isRemoved() || r.player.hasDisconnected()) {
            finish("el jugador se ha ido");
            return;
        }
        // Jugador quieto y mirando siempre igual (los golpes de los mobs lo mueven)
        if (r.player.level != r.level) {
            r.player.teleportTo(r.level, r.pos.x, r.pos.y, r.pos.z, 0f, 0f);
        } else if (r.player.position().distanceToSqr(r.pos) > 0.09 || Math.abs(r.player.getYRot()) > 0.5f || Math.abs(r.player.getXRot()) > 0.5f) {
            r.player.connection.teleport(r.pos.x, r.pos.y, r.pos.z, 0f, 0f);
        }
        // Los efectos se vuelven a poner cada segundo: al salir del Lobby, BTE Mobs (SacredPlaceHandler) quita
        // Resistencia, Velocidad y Saturacion al jugador, vengan de donde vengan
        if (r.player.tickCount % 20 == 0) maintainEffects(r.player);
        r.stateTicks++;
        switch (r.state) {
            case PREPARE -> {
                if (chunksReady(r) || r.stateTicks >= PREPARE_MAX_TICKS) {
                    r.prepareTicks = r.stateTicks;
                    if (Phase.values()[r.phaseIndex] == Phase.HORDA) spawnHorde(r);
                    r.state = State.SETTLE;
                    r.stateTicks = 0;
                }
            }
            case SETTLE -> {
                if (r.stateTicks >= SETTLE_TICKS) beginMeasure(r);
            }
            case MEASURE -> {
                r.tickNanos.add(dt);
                if (r.stateTicks % 20 == 0) sample(r);
                if (r.stateTicks >= r.measureTicks) {
                    endMeasure(r);
                    nextPhase(r);
                }
            }
        }
    }

    private static boolean chunksReady(Run r) {
        int radius = Math.min(r.server.getPlayerList().getViewDistance(), 12);
        int pcx = r.player.getBlockX() >> 4, pcz = r.player.getBlockZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (r.level.getChunkSource().getChunkNow(pcx + dx, pcz + dz) == null) return false;
            }
        }
        return true;
    }

    private static void beginMeasure(Run r) {
        r.state = State.MEASURE;
        r.stateTicks = 0;
        r.tickNanos.clear();
        r.entitySum = r.entityMax = r.mobSum = r.mobMax = r.chunkSum = r.samples = 0;
        r.maxHeap = 0;
        long[] gc = gcTotals();
        r.gcCount0 = gc[0];
        r.gcTime0 = gc[1];
        PlayBenchFrames.drain();
        PlayBenchFrames.recording = true;
        WorldgenProfiler.segment = "[fase " + Phase.values()[r.phaseIndex].name().toLowerCase() + "]";
    }

    private static void sample(Run r) {
        int entities = 0;
        for (Entity ignored : r.level.getAllEntities()) entities++;
        int mobs = r.level.getEntitiesOfClass(Mob.class, new AABB(r.pos, r.pos).inflate(64)).size();
        r.entitySum += entities;
        r.entityMax = Math.max(r.entityMax, entities);
        r.mobSum += mobs;
        r.mobMax = Math.max(r.mobMax, mobs);
        r.chunkSum += r.level.getChunkSource().getLoadedChunksCount();
        r.samples++;
        Runtime rt = Runtime.getRuntime();
        r.maxHeap = Math.max(r.maxHeap, rt.totalMemory() - rt.freeMemory());
    }

    private static void endMeasure(Run r) {
        PlayBenchFrames.recording = false;
        WorldgenProfiler.segment = "[preparando]";
        Phase phase = Phase.values()[r.phaseIndex];
        long[] t = r.tickNanos.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(t);
        long over50 = Arrays.stream(t).filter(v -> v > 50_000_000L).count();
        long[] f = PlayBenchFrames.drain();
        long[] fs = f.clone();
        Arrays.sort(fs);
        double frameMean = Arrays.stream(f).average().orElse(0) / 1e6;
        // "1% low": FPS medio del 1% de frames más lentos
        int worst = Math.max(1, fs.length / 100);
        double worstMean = 0;
        for (int i = fs.length - worst; i < fs.length && i >= 0; i++) worstMean += fs[i];
        worstMean = fs.length == 0 ? 0 : worstMean / worst / 1e6;
        long[] gc = gcTotals();
        long n = Math.max(1, r.samples);

        r.report.append(String.format("== %s (%s %s)%n", phase.name().toLowerCase(), r.level.dimension().location(),
                new BlockPos(r.pos).toShortString()));
        r.report.append(String.format("   preparacion: %.1f s hasta tener los chunks cargados%n", r.prepareTicks / 20.0));
        if (phase == Phase.HORDA) r.report.append("   horda: ").append(r.hordeInfo).append(System.lineSeparator());
        r.report.append(String.format("   servidor: %d ticks | MSPT media %.2f p50 %.2f p95 %.2f p99 %.2f max %.2f | >50 ms: %d%n",
                t.length, Arrays.stream(t).average().orElse(0) / 1e6, pct(t, 50), pct(t, 95), pct(t, 99),
                t.length == 0 ? 0 : t[t.length - 1] / 1e6, over50));
        r.report.append(String.format("   cliente: %d frames | FPS media %.1f | frame media %.2f p95 %.2f p99 %.2f max %.2f ms | 1%% low %.1f FPS%n",
                f.length, frameMean > 0 ? 1000 / frameMean : 0, frameMean, pct(fs, 95), pct(fs, 99),
                fs.length == 0 ? 0 : fs[fs.length - 1] / 1e6, worstMean > 0 ? 1000 / worstMean : 0));
        r.report.append(String.format("   memoria: GC %d pasadas, %d ms en total | maximo usado %d MB%n",
                gc[0] - r.gcCount0, gc[1] - r.gcTime0, r.maxHeap / (1024 * 1024)));
        r.report.append(String.format("   mundo: entidades media %.0f max %d | mobs a <64 bloques media %.1f max %d | chunks cargados media %.0f%n%n",
                (double) r.entitySum / n, r.entityMax, (double) r.mobSum / n, r.mobMax, (double) r.chunkSum / n));
        String line = String.format("[playbench] %s: MSPT media %.2f p99 %.2f | FPS media %.1f, 1%% low %.1f",
                phase.name().toLowerCase(), Arrays.stream(t).average().orElse(0) / 1e6, pct(t, 99),
                frameMean > 0 ? 1000 / frameMean : 0, worstMean > 0 ? 1000 / worstMean : 0);
        r.player.sendSystemMessage(Component.literal(line));
        BTEOptimizationFixes.LOGGER.info(line);
    }

    private static int finish(String why) {
        Run r = run;
        if (r == null) return 0;
        run = null;
        PlayBenchFrames.recording = false;
        WorldgenProfiler.stopSampler();
        WorldgenProfiler.segment = null;
        WorldgenProfiler.includeRender = false;
        Path prof = null;
        try {
            prof = WorldgenProfiler.write();
        } catch (IOException e) {
            BTEOptimizationFixes.LOGGER.warn("[playbench] No se pudo escribir el fichero de muestras", e);
        }
        clearHorde(r);

        MinecraftServer server = r.server;
        GameRules rules = server.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(r.ruleDaylight, server);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(r.ruleSpawning, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(r.ruleWeather, server);
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(r.ruleGriefing, server);
        server.overworld().setDayTime(r.dayTime);

        if (!r.player.isRemoved()) {
            for (MobEffect e : EFFECTS) r.player.removeEffect(e);
            r.player.getAbilities().invulnerable = r.previousInvulnerable;
            r.player.onUpdateAbilities();
            r.player.setGameMode(r.previousMode);
            ServerLevel back = server.getLevel(r.startDim);
            if (back != null) r.player.teleportTo(back, r.startPos.x, r.startPos.y, r.startPos.z, r.startYaw, r.startPitch);
        }

        try {
            Path file = FMLPaths.GAMEDIR.get().resolve("logs").resolve("bteopt_playbench_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                Runtime rt = Runtime.getRuntime();
                w.write("playbench (" + why + ") | seed " + server.getWorldData().worldGenSettings().seed()
                        + " | distancia de vision " + server.getPlayerList().getViewDistance()
                        + " | simulacion " + server.getPlayerList().getSimulationDistance()
                        + " | memoria maxima " + rt.maxMemory() / (1024 * 1024) + " MB"
                        + " | " + r.measureTicks / 20 + " s por fase" + System.lineSeparator());
                w.write("cliente: " + PlayBenchFrames.clientInfo + System.lineSeparator());
                w.write("muestras: " + (prof == null ? "-" : prof.getFileName()) + System.lineSeparator() + System.lineSeparator());
                w.write(r.report.toString());
            }
            String msg = "[playbench] Terminada (" + why + "). Informe en logs/" + file.getFileName();
            BTEOptimizationFixes.LOGGER.info(msg);
            if (!r.player.isRemoved()) r.player.sendSystemMessage(Component.literal(msg));
        } catch (IOException e) {
            BTEOptimizationFixes.LOGGER.warn("[playbench] No se pudo escribir el informe", e);
        }
        return 1;
    }

    private static double pct(long[] sorted, int p) {
        if (sorted.length == 0) return 0;
        int i = (int) Math.min(sorted.length - 1, Math.ceil(p / 100.0 * sorted.length) - 1);
        return sorted[Math.max(0, i)] / 1e6;
    }

    /** Por si algo atraviesa la invulnerabilidad: el jugador de la prueba no recibe ningún ataque ni puede morir. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        Run r = run;
        if (r != null && event.getEntity() == r.player) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Run r = run;
        if (r != null && event.getEntity() == r.player) {
            event.setCanceled(true);
            r.player.setHealth(r.player.getMaxHealth());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (run != null) finish("servidor parado");
    }
}
