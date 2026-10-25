package com.endersjourney.bteoptimizationfixes.debug;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Perfilador por muestreo, solo para la generación de mundo (herramienta de depuración, apagada por defecto).
 *
 *   /bteopt genprof start   empieza a tomar muestras
 *   /bteopt genprof stop    para y escribe logs/bteopt_genprof_<fecha>.txt
 *
 * Cada 5 ms toma la pila de llamadas de los hilos de generación (Worker-Main-*) y del hilo principal del servidor, y
 * cuenta cuántas veces aparece cada pila. Las pilas de los hilos de generación se separan según si en ese momento el
 * hilo está ejecutando la cola "worldgen" (trabajo en serie, el que marca el ritmo en el Overworld) o no (trabajo en
 * paralelo). Solo cuenta hilos en estado RUNNABLE que no estén esperando trabajo.
 *
 * El fichero tiene una línea por pila, de la raíz a la hoja separadas por ';', seguida del número de muestras (formato
 * "collapsed stacks"). Es mucho más ligero que spark: solo mira unos pocos hilos y no guarda nada más.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldgenProfiler {

    private static final int INTERVAL_MS = 5;
    private static final int MAX_DEPTH = 64;

    /** Hilos que están ahora mismo dentro de una tarea de la cola "worldgen" (lo marca ProcessorMailboxTimingMixin). */
    private static final Set<Long> IN_MAILBOX = ConcurrentHashMap.newKeySet();

    public static volatile boolean running = false;
    private static Thread sampler;

    private static final Map<String, Integer> FRAME_IDS = new HashMap<>();
    private static final List<String> FRAMES = new ArrayList<>();
    private static final Map<StackKey, int[]> STACKS = new HashMap<>();
    private static long samples, ticks;
    private static long startNanos;

    private WorldgenProfiler() {
    }

    public static void mailboxEnter() {
        if (running) IN_MAILBOX.add(Thread.currentThread().getId());
    }

    public static void mailboxExit() {
        if (running) IN_MAILBOX.remove(Thread.currentThread().getId());
    }

    private record StackKey(int[] frames) {
        @Override
        public boolean equals(Object o) {
            return o instanceof StackKey k && Arrays.equals(frames, k.frames);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(frames);
        }
    }

    private static int frameId(String frame) {
        Integer id = FRAME_IDS.get(frame);
        if (id == null) {
            id = FRAMES.size();
            FRAMES.add(frame);
            FRAME_IDS.put(frame, id);
        }
        return id;
    }

    private static boolean isIdle(StackTraceElement[] st) {
        if (st.length == 0) return true;
        String top = st[0].getClassName() + "." + st[0].getMethodName();
        if (top.startsWith("jdk.internal.misc.Unsafe.park") || top.startsWith("sun.misc.Unsafe.park")) return true;
        for (int i = 0; i < Math.min(st.length, 6); i++) {
            String m = st[i].getMethodName();
            if (st[i].getClassName().equals("java.util.concurrent.ForkJoinPool") && (m.equals("awaitWork") || m.equals("scan") && i == 0)) return true;
        }
        return false;
    }

    private static void sampleLoop() {
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long[] ids = new long[0];
        long serverId = -1;
        long lastRefresh = 0;
        while (running) {
            long now = System.nanoTime();
            if (now - lastRefresh > 1_000_000_000L) {
                lastRefresh = now;
                List<Long> list = new ArrayList<>();
                for (ThreadInfo ti : mx.getThreadInfo(mx.getAllThreadIds(), 0)) {
                    if (ti == null) continue;
                    String n = ti.getThreadName();
                    if (n.startsWith("Worker-Main-")) list.add(ti.getThreadId());
                    else if (n.equals("Server thread")) {
                        list.add(ti.getThreadId());
                        serverId = ti.getThreadId();
                    }
                }
                ids = list.stream().mapToLong(Long::longValue).toArray();
            }
            ThreadInfo[] infos = mx.getThreadInfo(ids, MAX_DEPTH);
            synchronized (STACKS) {
                ticks++;
                for (ThreadInfo ti : infos) {
                    if (ti == null || ti.getThreadState() != Thread.State.RUNNABLE) continue;
                    StackTraceElement[] st = ti.getStackTrace();
                    if (isIdle(st)) continue;
                    String root = ti.getThreadId() == serverId ? "[hilo principal]"
                            : IN_MAILBOX.contains(ti.getThreadId()) ? "[cola en serie]" : "[en paralelo]";
                    int[] frames = new int[st.length + 1];
                    frames[0] = frameId(root);
                    for (int i = 0; i < st.length; i++) {
                        StackTraceElement e = st[st.length - 1 - i]; // de la raíz a la hoja
                        frames[i + 1] = frameId(e.getClassName() + "." + e.getMethodName());
                    }
                    STACKS.computeIfAbsent(new StackKey(frames), k -> new int[1])[0]++;
                    samples++;
                }
            }
            try {
                Thread.sleep(INTERVAL_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void start() {
        stopSampler();
        synchronized (STACKS) {
            STACKS.clear();
            FRAMES.clear();
            FRAME_IDS.clear();
            samples = 0;
            ticks = 0;
        }
        IN_MAILBOX.clear();
        startNanos = System.nanoTime();
        running = true;
        sampler = new Thread(WorldgenProfiler::sampleLoop, "bteopt-genprof");
        sampler.setDaemon(true);
        sampler.start();
    }

    private static void stopSampler() {
        running = false;
        Thread t = sampler;
        sampler = null;
        if (t != null) {
            t.interrupt();
            try {
                t.join(2000);
            } catch (InterruptedException ignored) {
            }
        }
        IN_MAILBOX.clear();
    }

    private static Path write() throws IOException {
        Path dir = FMLPaths.GAMEDIR.get().resolve("logs");
        Files.createDirectories(dir);
        Path file = dir.resolve("bteopt_genprof_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")) + ".txt");
        synchronized (STACKS) {
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write("# muestras=" + samples + " rondas=" + ticks + " intervalo_ms=" + INTERVAL_MS
                        + " segundos=" + (System.nanoTime() - startNanos) / 1_000_000_000L + "\n");
                StringBuilder b = new StringBuilder();
                for (Map.Entry<StackKey, int[]> e : STACKS.entrySet()) {
                    b.setLength(0);
                    int[] f = e.getKey().frames();
                    for (int i = 0; i < f.length; i++) {
                        if (i > 0) b.append(';');
                        b.append(FRAMES.get(f[i]));
                    }
                    b.append(' ').append(e.getValue()[0]).append('\n');
                    w.write(b.toString());
                }
            }
        }
        return file;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("bteopt")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("genprof")
                        .then(Commands.literal("start").executes(WorldgenProfiler::cmdStart))
                        .then(Commands.literal("stop").executes(WorldgenProfiler::cmdStop))));
    }

    private static int cmdStart(CommandContext<CommandSourceStack> ctx) {
        start();
        ctx.getSource().sendSuccess(Component.literal("[bteopt-prof] Tomando muestras de la generación"), false);
        return 1;
    }

    private static int cmdStop(CommandContext<CommandSourceStack> ctx) {
        stopSampler();
        try {
            Path file = write();
            String line = "[bteopt-prof] " + samples + " muestras guardadas en " + file.getFileName();
            ctx.getSource().sendSuccess(Component.literal(line), false);
            BTEOptimizationFixes.LOGGER.info(line);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("[bteopt-prof] No se pudo escribir: " + e));
        }
        return 1;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (!running) return;
        stopSampler();
        try {
            BTEOptimizationFixes.LOGGER.info("[bteopt-prof] Muestras guardadas en logs/{}", write().getFileName());
        } catch (IOException e) {
            BTEOptimizationFixes.LOGGER.warn("[bteopt-prof] No se pudo escribir", e);
        }
    }
}
