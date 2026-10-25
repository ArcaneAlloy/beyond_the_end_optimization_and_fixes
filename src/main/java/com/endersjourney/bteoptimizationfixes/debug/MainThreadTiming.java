package com.endersjourney.bteoptimizationfixes.debug;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Parte de /bteopt genstats: en qué se va el tiempo del hilo principal del servidor ("Server thread").
 * Se enciende y se apaga junto con GenTiming.
 *
 * El hilo principal reparte los chunks entre fases, los da por terminados y ejecuta los eventos de Forge al cargar
 * chunks. Si está saturado, los hilos de generación se quedan esperando aunque haya trabajo pendiente.
 *
 * Categorías (tiempo exclusivo, suman el tiempo medido):
 *   - Ocioso: esperando a que llegue el siguiente tick o una tarea (BlockableEventLoop.waitForTasks sin nada abierto).
 *   - Esperando un chunk: waitForTasks dentro de un tick o de una tarea (el hilo principal se bloquea hasta que un
 *     hilo de generación termine un chunk).
 *   - Tareas: BlockableEventLoop.doRunTask (avanzar chunks de fase, convertirlos en chunks completos, tareas de mods),
 *     agrupadas por la clase que creó la tarea.
 *   - Tick del mundo: entre TickEvent.ServerTickEvent START y END (entidades, bloques, Chunky…).
 *   - Resto: lo que queda (bucle del servidor, guardado automático, etc.).
 * Aparte, el tiempo total de los eventos de Forge ChunkEvent.Load y EntityJoinLevelEvent en el hilo principal
 * (de su primer a su último oyente), que son donde muchos mods hacen trabajo al cargar chunks.
 *
 * SRG de los mixins (BlockableEventLoopTimingMixin), sacados de los volcados de hilos del modpack:
 *   m_5667_ = BlockableEventLoop.waitForTasks (aparece aparcado en LockSupport.parkNanos)
 *   m_6367_ = BlockableEventLoop.doRunTask (ServerChunkCache$MainThreadExecutor.m_6367_ -> super)
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MainThreadTiming {

    private static final int IDLE = 0, WAIT_CHUNK = 1, TASK = 2, TICK = 3;

    private static final class Frame {
        int kind;
        String name;
        long start;
        long child;
    }

    private static volatile Thread serverThread;
    private static long startNanos;
    private static long stopNanos;
    private static final long[] totals = new long[4];
    private static final Map<String, long[]> taskByOrigin = new HashMap<>();
    private static final long[] chunkLoadEvent = new long[2];
    private static final long[] entityJoinEvent = new long[2];
    private static final ArrayDeque<Frame> stack = new ArrayDeque<>();
    private static long chunkEventStart = -1;
    private static long entityEventStart = -1;
    private static int entityEventDepth = 0;

    private MainThreadTiming() {
    }

    private static boolean onServerThread() {
        return GenTiming.enabled && Thread.currentThread() == serverThread;
    }

    // Todo lo de abajo solo lo ejecuta el hilo principal, así que no necesita sincronización salvo al leer el resumen.

    static synchronized void reset() {
        Arrays.fill(totals, 0);
        taskByOrigin.clear();
        chunkLoadEvent[0] = chunkLoadEvent[1] = 0;
        entityJoinEvent[0] = entityJoinEvent[1] = 0;
        stack.clear();
        chunkEventStart = entityEventStart = -1;
        entityEventDepth = 0;
        startNanos = System.nanoTime();
        stopNanos = 0;
    }

    static synchronized void stop() {
        stopNanos = System.nanoTime();
    }

    private static void push(int kind, String name) {
        if (stack.size() > 256) stack.clear();
        Frame f = new Frame();
        f.kind = kind;
        f.name = name;
        f.start = System.nanoTime();
        stack.push(f);
    }

    private static void pop(int expectedKind) {
        if (stack.isEmpty() || stack.peek().kind != expectedKind) return;
        Frame f = stack.pop();
        long elapsed = System.nanoTime() - f.start;
        long own = Math.max(0, elapsed - f.child);
        totals[f.kind] += own;
        if (f.kind == TASK) {
            long[] v = taskByOrigin.computeIfAbsent(f.name, k -> new long[2]);
            v[0]++;
            v[1] += own;
        }
        Frame parent = stack.peek();
        if (parent != null) parent.child += elapsed;
    }

    // ---- Mixins ----

    public static void waitStart() {
        if (!onServerThread()) return;
        synchronized (MainThreadTiming.class) {
            push(stack.isEmpty() ? IDLE : WAIT_CHUNK, null);
        }
    }

    public static void waitEnd() {
        if (!onServerThread()) return;
        synchronized (MainThreadTiming.class) {
            if (!stack.isEmpty() && (stack.peek().kind == IDLE || stack.peek().kind == WAIT_CHUNK)) pop(stack.peek().kind);
        }
    }

    public static void taskStart(Object task) {
        if (!onServerThread()) return;
        String origin = origin(task);
        synchronized (MainThreadTiming.class) {
            push(TASK, origin);
        }
    }

    public static void taskEnd() {
        if (!onServerThread()) return;
        synchronized (MainThreadTiming.class) {
            pop(TASK);
        }
    }

    // ---- Eventos de Forge ----

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onServerTickFirst(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            serverThread = Thread.currentThread();
            if (!onServerThread()) return;
            synchronized (MainThreadTiming.class) {
                push(TICK, null);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onServerTickLast(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && onServerThread()) {
            synchronized (MainThreadTiming.class) {
                // Cierra lo que haya quedado abierto dentro del tick y el propio tick
                while (!stack.isEmpty() && stack.peek().kind != TICK) pop(stack.peek().kind);
                pop(TICK);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onChunkLoadFirst(ChunkEvent.Load event) {
        if (onServerThread() && !event.getLevel().isClientSide()) chunkEventStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onChunkLoadLast(ChunkEvent.Load event) {
        if (onServerThread() && chunkEventStart >= 0 && !event.getLevel().isClientSide()) {
            synchronized (MainThreadTiming.class) {
                chunkLoadEvent[0]++;
                chunkLoadEvent[1] += System.nanoTime() - chunkEventStart;
            }
            chunkEventStart = -1;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onEntityJoinFirst(EntityJoinLevelEvent event) {
        if (onServerThread() && !event.getLevel().isClientSide()) {
            if (entityEventDepth++ == 0) entityEventStart = System.nanoTime();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onEntityJoinLast(EntityJoinLevelEvent event) {
        if (onServerThread() && entityEventDepth > 0 && !event.getLevel().isClientSide()) {
            if (--entityEventDepth == 0 && entityEventStart >= 0) {
                synchronized (MainThreadTiming.class) {
                    entityJoinEvent[0]++;
                    entityJoinEvent[1] += System.nanoTime() - entityEventStart;
                }
                entityEventStart = -1;
            }
        }
    }

    // ---- Origen de cada tarea ----

    private static final Map<Class<?>, Field> RUNNABLE_FIELD = new HashMap<>();

    /** Nombre corto de la clase que creó la tarea; las TickTask del servidor se desenvuelven para ver su Runnable. */
    private static String origin(Object task) {
        Object t = task;
        if (t != null && t.getClass().getSimpleName().equals("TickTask")) {
            Field f = RUNNABLE_FIELD.computeIfAbsent(t.getClass(), c -> {
                for (Field field : c.getDeclaredFields()) {
                    if (Runnable.class.isAssignableFrom(field.getType())) {
                        try {
                            field.setAccessible(true);
                            return field;
                        } catch (Throwable ignored) {
                        }
                    }
                }
                return null;
            });
            if (f != null) {
                try {
                    Object inner = f.get(t);
                    if (inner != null) t = inner;
                } catch (Throwable ignored) {
                }
            }
        }
        if (t == null) return "?";
        String n = t.getClass().getName();
        int lambda = n.indexOf("$$Lambda");
        if (lambda >= 0) n = n.substring(0, lambda);
        int dot = n.lastIndexOf('.');
        return dot >= 0 ? n.substring(dot + 1) : n;
    }

    // ---- Resumen ----

    private static String pct(long part, long total) {
        return total <= 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", 100.0 * part / total);
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.0f", nanos / 1_000_000.0);
    }

    static synchronized long wallNanos() {
        if (startNanos == 0) return 0;
        return (stopNanos > 0 ? stopNanos : System.nanoTime()) - startNanos;
    }

    static synchronized List<String> summaryLines() {
        List<String> lines = new ArrayList<>();
        long wall = wallNanos();
        if (wall <= 0) return lines;
        long known = totals[IDLE] + totals[WAIT_CHUNK] + totals[TASK] + totals[TICK];
        long rest = Math.max(0, wall - known);
        lines.add("[bteopt-gen] Hilo principal del servidor (" + ms(wall) + " ms medidos):");
        lines.add("  " + pct(totals[IDLE], wall) + "  ocioso (esperando el siguiente tick o una tarea)");
        lines.add("  " + pct(totals[WAIT_CHUNK], wall) + "  bloqueado esperando un chunk");
        lines.add("  " + pct(totals[TASK], wall) + "  tareas (chunks y servidor)");
        lines.add("  " + pct(totals[TICK], wall) + "  tick del mundo");
        lines.add("  " + pct(rest, wall) + "  resto (bucle del servidor, guardado…)");
        taskByOrigin.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1])).limit(6).forEach(e ->
                lines.add("     tarea " + e.getKey() + ": " + pct(e.getValue()[1], wall) + " (" + e.getValue()[0] + " veces)"));
        lines.add("  Eventos Forge: carga de chunk " + ms(chunkLoadEvent[1]) + " ms (" + chunkLoadEvent[0] + " veces), entidades que entran al mundo "
                + ms(entityJoinEvent[1]) + " ms (" + entityJoinEvent[0] + " veces)");
        return lines;
    }

    static synchronized List<String[]> csvRows() {
        List<String[]> rows = new ArrayList<>();
        String[] names = {"ocioso", "bloqueado esperando un chunk", "tareas", "tick del mundo"};
        for (int i = 0; i < 4; i++) rows.add(new String[]{"hilo principal", names[i], "", ms(totals[i])});
        taskByOrigin.forEach((k, v) -> rows.add(new String[]{"hilo principal: tarea", k, String.valueOf(v[0]), ms(v[1])}));
        rows.add(new String[]{"hilo principal: evento", "ChunkEvent.Load", String.valueOf(chunkLoadEvent[0]), ms(chunkLoadEvent[1])});
        rows.add(new String[]{"hilo principal: evento", "EntityJoinLevelEvent", String.valueOf(entityJoinEvent[0]), ms(entityJoinEvent[1])});
        rows.add(new String[]{"hilo principal", "tiempo medido (reloj)", "", ms(wallNanos())});
        return rows;
    }
}
