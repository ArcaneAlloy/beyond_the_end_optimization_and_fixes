package com.endersjourney.bteoptimizationfixes.debug;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;

/**
 * Medición de dónde se va el tiempo al generar chunks (herramienta de depuración, apagada por defecto).
 *
 *   /bteopt genstats start   empieza a medir (pone los contadores a cero)
 *   /bteopt genstats         resumen en el chat y CSV completo en logs/bteopt_genstats_<fecha>.csv
 *   /bteopt genstats stop    deja de medir (los datos se conservan hasta el siguiente start)
 * Al cerrar el mundo, si hay datos, se escriben también el resumen y el CSV.
 *
 * Qué se mide (mixins en mixin/worldgen):
 *   - Fases del chunk (ChunkStatus.generate): tiempo de cada fase en el hilo que la ejecuta, incluyendo todo lo que
 *     haga dentro. Las fases "biomes", "noise" y "light" mandan su trabajo pesado a otra tarea, así que su fila solo
 *     cuenta la parte de preparación; por eso se miden aparte:
 *   - "Relleno del terreno (noise)": NoiseBasedChunkGenerator.doFill, donde se evalúa el ruido del terreno
 *     (Tectonic, Terralith, Nullscape...) y se colocan los bloques base.
 *   - "Relleno de biomas (biomes)": LevelChunkSection.fillBiomesFromNoise, la elección de bioma de cada sección.
 *   - Features: cada PlacedFeature (árboles, rocas, menas, vegetación...), con su id de registro.
 *   - Estructuras: cada StructureStart.placeInChunk (colocar la parte de la estructura que cae en el chunk).
 *   - Inicio de estructuras: cada Structure.generate (fase structure_starts), es decir, decidir si una estructura
 *     empieza en el chunk: buscar su punto de generación, montar las piezas del jigsaw y comprobar el bioma.
 *   - Detalles: trabajos concretos que pueden estar dentro de varias fases (reglas de superficie, altura preliminar de
 *     la superficie, columnas de ruido sueltas, crear el NoiseChunk, comprobar el bioma de una estructura). Cada
 *     detalle se apunta junto con la fase o cosa en la que ocurre, por ejemplo "Columna de ruido · en inicio de
 *     estructuras".
 *   Features, estructuras, inicios de estructura y detalles cuentan tiempo exclusivo: si una feature coloca otras
 *   dentro, o una estructura coloca features, cada tiempo se apunta solo a quien lo gasta y no se cuenta dos veces.
 *
 *   - Consultas de bioma dentro de las reglas de superficie (BiomeManager.getBiome): se cuentan todas, pero solo se
 *     cronometra 1 de cada 16 para no inflar el tiempo de la superficie; el total se estima a partir de esa muestra.
 *   - Columnas de ruido sueltas: además del tiempo, cuántas se piden en una posición (x, z) ya pedida antes. Es el
 *     máximo que podría ahorrar una caché de columnas.
 *   - Trazas: de dónde vienen las llamadas a "Crear el NoiseChunk" y "Columna de ruido suelta" (pila de llamadas
 *     agrupada), en logs/bteopt_genstats_trazas_<fecha>.txt.
 *   - Cola de generación: tiempo que la cola "worldgen" (ProcessorMailbox) está ejecutando tareas. Esa cola ejecuta de
 *     una en una todas las fases que no se mandan a otra tarea, así que su ocupación dice si es ella el límite.
 *
 * Los tiempos son la suma de todos los hilos de generación, que trabajan en paralelo: el total supera al tiempo real
 * de Chunky. Lo útil es el reparto (qué porcentaje se lleva cada cosa).
 *
 * Además, MainThreadTiming mide en paralelo el hilo principal del servidor (ocioso, bloqueado esperando chunks,
 * tareas, tick y eventos de Forge de carga de chunk), y el resumen da la ocupación media de los hilos de generación.
 *
 * Coste: con la medición apagada, cada punto medido solo lee un booleano. Encendida, dos System.nanoTime y una suma
 * en un mapa del propio hilo, sin bloqueos entre hilos.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GenTiming {

    public static volatile boolean enabled = false;

    private static final int STATUS = 0, FEATURE = 1, STRUCTURE = 2, OTHER = 3, STRUCTURE_GEN = 4, DETAIL = 5;
    private static final int KINDS = 6;
    public static final String NOISE_FILL = "Relleno del terreno (noise)";
    public static final String BIOME_FILL = "Relleno de biomas (biomes)";
    public static final String SURFACE_RULES = "Reglas de superficie";
    public static final String PRELIMINARY_SURFACE = "Altura preliminar de la superficie";
    public static final String NOISE_COLUMN = "Columna de ruido suelta (getBaseHeight/getBaseColumn)";
    public static final String CREATE_NOISE_CHUNK = "Crear el NoiseChunk";
    public static final String STRUCTURE_BIOME_CHECK = "Comprobar el bioma de la estructura";
    private static final String IN_STRUCTURE_GEN = "inicio de estructuras";

    /** Acumuladores de un hilo: clave -> {llamadas, nanos}. Solo escribe su hilo; el resumen los lee con el mismo cerrojo. */
    private static final class ThreadStats {
        final List<Map<Object, long[]>> maps = List.of(new HashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>(), new HashMap<>(),
                new IdentityHashMap<>(), new HashMap<>());

        synchronized void add(int kind, Object key, long nanos) {
            long[] v = maps.get(kind).computeIfAbsent(key, k -> new long[2]);
            v[0]++;
            v[1] += nanos;
        }

        synchronized void clear() {
            maps.forEach(Map::clear);
        }
    }

    private static final class Frame {
        int kind;
        Object key;
        long start;
        long child;
    }

    private static final ConcurrentLinkedQueue<ThreadStats> ALL = new ConcurrentLinkedQueue<>();
    private static final ThreadLocal<ThreadStats> STATS = ThreadLocal.withInitial(() -> {
        ThreadStats s = new ThreadStats();
        ALL.add(s);
        return s;
    });
    private static final ThreadLocal<ArrayDeque<Frame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private GenTiming() {
    }

    // ---- Llamado desde los mixins ----

    private static void push(int kind, Object key) {
        ArrayDeque<Frame> stack = STACK.get();
        if (stack.size() > 256) stack.clear(); // por si alguna excepción dejó marcos sin cerrar
        Frame f = new Frame();
        f.kind = kind;
        f.key = key;
        f.start = System.nanoTime();
        stack.push(f);
    }

    private static void pop() {
        ArrayDeque<Frame> stack = STACK.get();
        if (stack.isEmpty()) return;
        Frame f = stack.pop();
        long elapsed = System.nanoTime() - f.start;
        // Fases y rellenos: tiempo total. Features, estructuras, inicios de estructura y detalles: tiempo exclusivo.
        long counted = (f.kind == STATUS || f.kind == OTHER) ? elapsed : elapsed - f.child;
        STATS.get().add(f.kind, f.key, Math.max(0, counted));
        Frame parent = stack.peek();
        if (parent != null) parent.child += elapsed;
    }

    public static void statusStart(Object status) {
        if (enabled) push(STATUS, ((ChunkStatus) status).getName());
    }

    public static void featureStart(Object placedFeature) {
        if (enabled) push(FEATURE, placedFeature);
    }

    public static void structureStart(Object structure) {
        if (enabled) push(STRUCTURE, structure);
    }

    public static void otherStart(String what) {
        if (enabled) push(OTHER, what);
    }

    public static void structureGenStart(Object structure) {
        if (enabled) push(STRUCTURE_GEN, structure);
    }

    /** Un detalle se apunta con la fase o cosa en la que ocurre (el marco más cercano que no sea otro detalle). */
    public static void detailStart(String what) {
        if (!enabled) return;
        String where = "fuera de las fases medidas";
        for (Frame f : STACK.get()) { // de arriba abajo
            if (f.kind == DETAIL) continue;
            where = switch (f.kind) {
                case FEATURE -> "features";
                case STRUCTURE -> "colocar estructuras";
                case STRUCTURE_GEN -> IN_STRUCTURE_GEN;
                default -> String.valueOf(f.key);
            };
            break;
        }
        String key = what + " \u00b7 en " + where;
        if (what == CREATE_NOISE_CHUNK || what == NOISE_COLUMN) trace(key);
        push(DETAIL, key);
    }

    // ---- Trazas agrupadas ----

    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.LongAdder> TRACES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int TRACE_DEPTH = 28;

    private static void trace(String label) {
        StackTraceElement[] st = new Throwable().getStackTrace();
        StringBuilder b = new StringBuilder(label);
        int added = 0;
        for (StackTraceElement e : st) {
            String cls = e.getClassName();
            if (cls.startsWith("com.endersjourney.bteoptimizationfixes.debug.")) continue;
            b.append("\n    ").append(cls).append('.').append(e.getMethodName()).append(':').append(e.getLineNumber());
            if (++added >= TRACE_DEPTH) break;
        }
        TRACES.computeIfAbsent(b.toString(), k -> new java.util.concurrent.atomic.LongAdder()).increment();
    }

    private static Path writeTraces() throws IOException {
        Path dir = FMLPaths.GAMEDIR.get().resolve("logs");
        Files.createDirectories(dir);
        Path file = dir.resolve("bteopt_genstats_trazas_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")) + ".txt");
        List<Map.Entry<String, java.util.concurrent.atomic.LongAdder>> list = new ArrayList<>(TRACES.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()));
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("Pilas de llamadas agrupadas (las " + TRACE_DEPTH + " llamadas más internas), de más a menos frecuente.\n\n");
            for (Map.Entry<String, java.util.concurrent.atomic.LongAdder> e : list) {
                w.write(e.getValue().sum() + " veces: " + e.getKey() + "\n\n");
            }
        }
        return file;
    }

    // ---- Columnas de ruido sueltas repetidas ----

    private static final java.util.concurrent.atomic.LongAdder COLUMNS_TOTAL = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder COLUMNS_REPEATED = new java.util.concurrent.atomic.LongAdder();
    private static final Set<Long> COLUMNS_SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void noiseColumnAt(int x, int z) {
        if (!enabled) return;
        COLUMNS_TOTAL.increment();
        if (!COLUMNS_SEEN.add(((long) x << 32) | (z & 0xFFFFFFFFL))) COLUMNS_REPEATED.increment();
    }

    // ---- Consultas de bioma dentro de las reglas de superficie (muestreadas) ----

    private static final class SurfaceState {
        int depth;
        int counter;
        long biomeStart;
    }

    private static final ThreadLocal<SurfaceState> SURFACE = ThreadLocal.withInitial(SurfaceState::new);
    private static final java.util.concurrent.atomic.LongAdder BIOME_CALLS = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder BIOME_SAMPLED_CALLS = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder BIOME_SAMPLED_NANOS = new java.util.concurrent.atomic.LongAdder();

    public static void surfaceRulesStart() {
        if (!enabled) return;
        SURFACE.get().depth++;
        detailStart(SURFACE_RULES);
    }

    public static void surfaceRulesEnd() {
        SurfaceState st = SURFACE.get();
        if (st.depth > 0) st.depth--;
        end();
    }

    public static void biomeStart() {
        if (!enabled) return;
        SurfaceState st = SURFACE.get();
        if (st.depth == 0) return;
        BIOME_CALLS.increment();
        if ((++st.counter & 15) == 0) st.biomeStart = System.nanoTime();
    }

    public static void biomeEnd() {
        if (!enabled) return;
        SurfaceState st = SURFACE.get();
        if (st.biomeStart == 0) return;
        BIOME_SAMPLED_NANOS.add(System.nanoTime() - st.biomeStart);
        BIOME_SAMPLED_CALLS.increment();
        st.biomeStart = 0;
    }

    private static void resetExtras() {
        MAILBOX_NANOS.reset();
        MAILBOX_TASKS.reset();
        TRACES.clear();
        COLUMNS_TOTAL.reset();
        COLUMNS_REPEATED.reset();
        COLUMNS_SEEN.clear();
        BIOME_CALLS.reset();
        BIOME_SAMPLED_CALLS.reset();
        BIOME_SAMPLED_NANOS.reset();
    }

    public static void end() {
        if (enabled) pop();
    }

    // ---- Cola "worldgen" ----

    private static final java.util.concurrent.atomic.LongAdder MAILBOX_NANOS = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder MAILBOX_TASKS = new java.util.concurrent.atomic.LongAdder();
    private static final ThreadLocal<long[]> MAILBOX_START = ThreadLocal.withInitial(() -> new long[1]);

    public static void mailboxStart() {
        MAILBOX_START.get()[0] = System.nanoTime();
    }

    public static void mailboxEnd(boolean ranTask) {
        long[] start = MAILBOX_START.get();
        if (start[0] == 0) return;
        long elapsed = System.nanoTime() - start[0];
        start[0] = 0;
        if (!enabled) return;
        MAILBOX_NANOS.add(elapsed);
        if (ranTask) MAILBOX_TASKS.increment();
    }

    // ---- Resumen ----

    private record Row(String type, String name, long calls, long nanos) {
    }

    private static List<Row> collect(MinecraftServer server) {
        Map<Object, String> featureNames = new IdentityHashMap<>();
        Map<Object, String> structureNames = new IdentityHashMap<>();
        if (server != null) {
            for (Map.Entry<ResourceKey<PlacedFeature>, PlacedFeature> e : server.registryAccess().registryOrThrow(Registry.PLACED_FEATURE_REGISTRY).entrySet()) {
                featureNames.put(e.getValue(), e.getKey().location().toString());
            }
            for (Map.Entry<ResourceKey<Structure>, Structure> e : server.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY).entrySet()) {
                structureNames.put(e.getValue(), e.getKey().location().toString());
            }
        }
        String[] types = {"fase", "feature", "estructura", "relleno", "inicio de estructura", "detalle"};
        Map<String, long[]> merged = new LinkedHashMap<>();
        for (ThreadStats s : ALL) {
            synchronized (s) {
                for (int kind = 0; kind < KINDS; kind++) {
                    for (Map.Entry<Object, long[]> e : s.maps.get(kind).entrySet()) {
                        String name = switch (kind) {
                            case FEATURE -> featureNames.getOrDefault(e.getKey(), unregisteredFeature(e.getKey()));
                            case STRUCTURE, STRUCTURE_GEN -> structureNames.getOrDefault(e.getKey(), "(sin registrar) " + e.getKey().getClass().getSimpleName());
                            default -> String.valueOf(e.getKey());
                        };
                        long[] v = merged.computeIfAbsent(types[kind] + "\u0000" + name, k -> new long[2]);
                        v[0] += e.getValue()[0];
                        v[1] += e.getValue()[1];
                    }
                }
            }
        }
        List<Row> rows = new ArrayList<>();
        merged.forEach((k, v) -> {
            String[] p = k.split("\u0000", 2);
            rows.add(new Row(p[0], p[1], v[0], v[1]));
        });
        rows.add(new Row("cola", "worldgen: tiempo ocupada", MAILBOX_TASKS.sum(), MAILBOX_NANOS.sum()));
        rows.add(new Row("bioma en superficie", "consultas (tiempo estimado por muestreo)", BIOME_CALLS.sum(), estimatedBiomeNanos()));
        rows.add(new Row("columnas de ruido", "pedidas", COLUMNS_TOTAL.sum(), 0));
        rows.add(new Row("columnas de ruido", "repetidas en la misma posición", COLUMNS_REPEATED.sum(), 0));
        rows.sort(Comparator.comparingLong(Row::nanos).reversed());
        return rows;
    }

    /** Features sin nombre propio (las que van dentro de otras o dentro de estructuras): se nombran por su tipo. */
    private static String unregisteredFeature(Object key) {
        try {
            ResourceLocation type = ForgeRegistries.FEATURES.getKey(((PlacedFeature) key).feature().value().feature());
            return "(dentro de otra) " + type;
        } catch (Throwable t) {
            return "(dentro de otra) desconocida";
        }
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1_000_000.0);
    }

    private static String pct(long part, long total) {
        return total <= 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", 100.0 * part / total);
    }

    private static List<String> summaryLines(List<Row> rows) {
        List<String> lines = new ArrayList<>();
        // Tiempo de referencia: fases (sin contar dentro features/estructuras, que ya están dentro de "features") + rellenos.
        long phases = rows.stream().filter(r -> r.type().equals("fase")).mapToLong(Row::nanos).sum();
        long fills = rows.stream().filter(r -> r.type().equals("relleno")).mapToLong(Row::nanos).sum();
        long total = phases + fills;
        lines.add("[bteopt-gen] Tiempo medido (suma de hilos): " + ms(total) + " ms" + (enabled ? " (midiendo)" : " (parado)"));
        lines.add("[bteopt-gen] Fases y rellenos:");
        rows.stream().filter(r -> r.type().equals("fase") || r.type().equals("relleno")).limit(14).forEach(r ->
                lines.add("  " + pct(r.nanos(), total) + "  " + r.name() + "  (" + ms(r.nanos()) + " ms)"));
        long features = rows.stream().filter(r -> r.type().equals("feature")).mapToLong(Row::nanos).sum();
        long structures = rows.stream().filter(r -> r.type().equals("estructura")).mapToLong(Row::nanos).sum();
        lines.add("[bteopt-gen] Dentro de las fases: features " + pct(features, total) + ", colocar estructuras " + pct(structures, total));
        lines.add("[bteopt-gen] Features que más tardan:");
        rows.stream().filter(r -> r.type().equals("feature")).limit(10).forEach(r ->
                lines.add("  " + pct(r.nanos(), total) + "  " + r.name() + "  (" + r.calls() + " veces)"));
        lines.add("[bteopt-gen] Estructuras que más tardan al colocarse:");
        rows.stream().filter(r -> r.type().equals("estructura")).limit(5).forEach(r ->
                lines.add("  " + pct(r.nanos(), total) + "  " + r.name() + "  (" + r.calls() + " veces)"));
        // Fase structure_starts: qué estructuras cuesta decidir y cuánto queda fuera (comprobar los sitios posibles)
        long startsPhase = rows.stream().filter(r -> r.type().equals("fase") && r.name().equals("structure_starts")).mapToLong(Row::nanos).sum();
        long startsGen = rows.stream().filter(r -> r.type().equals("inicio de estructura")).mapToLong(Row::nanos).sum()
                + rows.stream().filter(r -> r.type().equals("detalle") && r.name().endsWith("en " + IN_STRUCTURE_GEN)).mapToLong(Row::nanos).sum();
        lines.add("[bteopt-gen] Inicio de estructuras que más tarda (dentro de structure_starts, " + ms(startsPhase) + " ms):");
        rows.stream().filter(r -> r.type().equals("inicio de estructura")).limit(8).forEach(r ->
                lines.add("  " + pct(r.nanos(), total) + "  " + r.name() + "  (" + r.calls() + " veces)"));
        lines.add("  " + pct(Math.max(0, startsPhase - startsGen), total) + "  resto de structure_starts (comprobar sitios posibles, buscar inicios ya guardados...)");
        long rules = rows.stream().filter(r -> r.type().equals("detalle") && r.name().startsWith(SURFACE_RULES)).mapToLong(Row::nanos).sum();
        lines.add(String.format(Locale.ROOT, "[bteopt-gen] Reglas de superficie: %s ms; consultas de bioma dentro: %d, unos %s ms estimados (%s de las reglas)",
                ms(rules), BIOME_CALLS.sum(), ms(estimatedBiomeNanos()), pct(estimatedBiomeNanos(), rules)));
        long cols = COLUMNS_TOTAL.sum(), rep = COLUMNS_REPEATED.sum();
        lines.add(String.format(Locale.ROOT, "[bteopt-gen] Columnas de ruido sueltas: %d pedidas, %d en una posición ya pedida antes (%s)",
                cols, rep, pct(rep, cols)));
        lines.add("[bteopt-gen] Detalles que más tardan:");
        rows.stream().filter(r -> r.type().equals("detalle")).limit(10).forEach(r ->
                lines.add("  " + pct(r.nanos(), total) + "  " + r.name() + "  (" + r.calls() + " veces)"));
        // Ocupación media de los hilos de generación en lo medido: tiempo medido / tiempo de reloj
        long wall = MainThreadTiming.wallNanos();
        if (wall > 0) {
            int workers = Util.backgroundExecutor() instanceof ForkJoinPool pool ? pool.getParallelism() : -1;
            lines.add(String.format(Locale.ROOT, "[bteopt-gen] Hilos de generación ocupados de media (en lo medido): %.1f de %s",
                    (double) total / wall, workers > 0 ? String.valueOf(workers) : "?"));
            long busy = MAILBOX_NANOS.sum();
            long tasks = MAILBOX_TASKS.sum();
            lines.add(String.format(Locale.ROOT, "[bteopt-gen] Cola de generación \"worldgen\" (fases de una en una): ocupada %s del tiempo medido (%d tareas, %.2f ms de media)",
                    pct(busy, wall), tasks, tasks == 0 ? 0.0 : busy / 1_000_000.0 / tasks));
        }
        lines.addAll(MainThreadTiming.summaryLines());
        return lines;
    }

    private static long estimatedBiomeNanos() {
        long sampled = BIOME_SAMPLED_CALLS.sum();
        return sampled == 0 ? 0 : (long) ((double) BIOME_SAMPLED_NANOS.sum() * BIOME_CALLS.sum() / sampled);
    }

    private static Path writeCsv(List<Row> rows) throws IOException {
        Path dir = FMLPaths.GAMEDIR.get().resolve("logs");
        Files.createDirectories(dir);
        Path file = dir.resolve("bteopt_genstats_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")) + ".csv");
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("tipo;nombre;llamadas;ms_total;ms_medio\n");
            for (Row r : rows) {
                w.write(r.type() + ";" + r.name() + ";" + r.calls() + ";" + ms(r.nanos()).replace('.', ',') + ";"
                        + String.format(Locale.ROOT, "%.4f", r.calls() == 0 ? 0 : r.nanos() / 1_000_000.0 / r.calls()).replace('.', ',') + "\n");
            }
            for (String[] r : MainThreadTiming.csvRows()) {
                w.write(r[0] + ";" + r[1] + ";" + r[2] + ";" + r[3] + ";\n");
            }
        }
        return file;
    }

    private static boolean hasData() {
        for (ThreadStats s : ALL) {
            synchronized (s) {
                for (Map<Object, long[]> m : s.maps) if (!m.isEmpty()) return true;
            }
        }
        return false;
    }

    // ---- Comando y cierre ----

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("bteopt")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("genstats")
                        .executes(GenTiming::show)
                        .then(Commands.literal("start").executes(GenTiming::start))
                        .then(Commands.literal("stop").executes(GenTiming::stop))));
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        ALL.forEach(ThreadStats::clear);
        resetExtras();
        MainThreadTiming.reset();
        enabled = true;
        ctx.getSource().sendSuccess(Component.literal("[bteopt-gen] Midiendo la generación (contadores a cero)"), false);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        enabled = false;
        MainThreadTiming.stop();
        ctx.getSource().sendSuccess(Component.literal("[bteopt-gen] Medición parada; /bteopt genstats para ver el resultado"), false);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        List<Row> rows = collect(ctx.getSource().getServer());
        for (String line : summaryLines(rows)) {
            ctx.getSource().sendSuccess(Component.literal(line), false);
            BTEOptimizationFixes.LOGGER.info(line);
        }
        try {
            ctx.getSource().sendSuccess(Component.literal("[bteopt-gen] Detalle en " + writeCsv(rows).getFileName()), false);
            if (!TRACES.isEmpty()) ctx.getSource().sendSuccess(Component.literal("[bteopt-gen] Trazas en " + writeTraces().getFileName()), false);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("[bteopt-gen] No se pudo escribir el CSV: " + e));
        }
        return 1;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        enabled = false;
        MainThreadTiming.stop();
        if (!hasData()) return;
        List<Row> rows = collect(event.getServer());
        summaryLines(rows).forEach(BTEOptimizationFixes.LOGGER::info);
        try {
            BTEOptimizationFixes.LOGGER.info("[bteopt-gen] Detalle en logs/{}", writeCsv(rows).getFileName());
        } catch (IOException e) {
            BTEOptimizationFixes.LOGGER.warn("[bteopt-gen] No se pudo escribir el CSV", e);
        }
        try {
            if (!TRACES.isEmpty()) BTEOptimizationFixes.LOGGER.info("[bteopt-gen] Trazas en logs/{}", writeTraces().getFileName());
        } catch (IOException e) {
            BTEOptimizationFixes.LOGGER.warn("[bteopt-gen] No se pudieron escribir las trazas", e);
        }
        ALL.forEach(ThreadStats::clear);
        resetExtras();
    }
}
