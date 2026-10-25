package com.endersjourney.bteoptimizationfixes.debug;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Medición del coste de cargar estructuras NBT (herramienta de depuración).
 *
 * ModernFix (mixin.perf.cache_upgraded_structures, activo por defecto) sustituye la carga de estructuras de
 * StructureTemplateManager por CachingStructureManager.readStructureTag. Ahí cada estructura acaba en uno de tres casos:
 *   - ACTUAL: ya está en la versión de datos del juego (3120 en 1.19.2). Solo se lee.
 *   - CACHE:  es antigua, pero ModernFix ya tenía guardada su versión convertida en modernfix/structureCacheV1.
 *             Se lee el archivo original, se calcula su SHA-256 y se lee la copia convertida.
 *   - DFU:    es antigua y no estaba en la caché: pasa por el DataFixerUpper de Mojang y se guarda en la caché.
 *             La primera vez que ocurre en la sesión, ese tiempo incluye además construir el DataFixerUpper
 *             (ModernFix lo crea solo cuando alguien lo necesita).
 *
 * StructureDfuTimingMixin mide cada carga y la apunta aquí. Para ver los resultados:
 *   /bteopt nbtstats         resumen en el chat y CSV completo en logs/bteopt_structure_timing_<fecha>.csv
 *   /bteopt nbtstats reset   pone los contadores a cero (por ejemplo, justo antes de lanzar Chunky)
 * Al cerrar el mundo se escribe también el resumen en el log y el CSV, para comparar partidas sin usar el comando.
 *
 * El coste de medir es despreciable (dos System.nanoTime por estructura, y cada estructura se carga una vez).
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StructureLoadTiming {

    public enum Kind { ACTUAL, CACHE, DFU }

    public record Entry(String id, int dataVersion, Kind kind, long totalNanos, long dfuNanos, boolean firstDfu, String thread) {
    }

    private static final class Ctx {
        ResourceLocation id;
        long start;
        int dataVersion = -1;
        long dfuNanos = -1;
        boolean firstDfu;
    }

    private static final ThreadLocal<Ctx> CURRENT = ThreadLocal.withInitial(Ctx::new);
    private static final ConcurrentLinkedQueue<Entry> ENTRIES = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean DFU_USED = new AtomicBoolean(false);

    private StructureLoadTiming() {
    }

    // ---- Llamado desde StructureDfuTimingMixin ----

    public static void start(ResourceLocation id) {
        Ctx c = CURRENT.get();
        c.id = id;
        c.start = System.nanoTime();
        c.dataVersion = -1;
        c.dfuNanos = -1;
        c.firstDfu = false;
    }

    /** Versión de datos del archivo tal como viene en el mod o datapack (500 si no la tiene, igual que el juego). */
    public static void dataVersion(int version) {
        CURRENT.get().dataVersion = version;
    }

    /** true si es la primera conversión de la sesión (su tiempo incluye construir el DataFixerUpper). */
    public static boolean beginDfu() {
        return DFU_USED.compareAndSet(false, true);
    }

    public static void dfu(long nanos, boolean first) {
        Ctx c = CURRENT.get();
        c.dfuNanos = nanos;
        c.firstDfu = first;
        if (first) {
            BTEOptimizationFixes.LOGGER.info("[bteopt-nbt] Primera conversión DFU de la sesión: {} (DataVersion {}) en {} ms, incluye construir el DataFixerUpper",
                    c.id, c.dataVersion, ms(nanos));
        }
    }

    public static void end() {
        Ctx c = CURRENT.get();
        if (c.id == null) return;
        long total = System.nanoTime() - c.start;
        int current = SharedConstants.getCurrentVersion().getWorldVersion();
        Kind kind = c.dfuNanos >= 0 ? Kind.DFU : (c.dataVersion >= 0 && c.dataVersion < current ? Kind.CACHE : Kind.ACTUAL);
        ENTRIES.add(new Entry(c.id.toString(), c.dataVersion, kind, total, Math.max(0, c.dfuNanos), c.firstDfu, Thread.currentThread().getName()));
        if (kind == Kind.DFU) {
            BTEOptimizationFixes.LOGGER.info("[bteopt-nbt] DFU {} (DataVersion {}): {} ms en total, {} ms convirtiendo [{}]",
                    c.id, c.dataVersion, ms(total), ms(c.dfuNanos), Thread.currentThread().getName());
        }
        c.id = null;
    }

    // ---- Resumen ----

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0);
    }

    private static List<String> summaryLines() {
        long[] count = new long[3];
        long[] nanos = new long[3];
        long dfuOnly = 0;
        long firstDfu = 0;
        for (Entry e : ENTRIES) {
            count[e.kind().ordinal()]++;
            nanos[e.kind().ordinal()] += e.totalNanos();
            dfuOnly += e.dfuNanos();
            if (e.firstDfu()) firstDfu = e.dfuNanos();
        }
        List<String> lines = new ArrayList<>();
        lines.add("[bteopt-nbt] Estructuras cargadas: " + ENTRIES.size());
        lines.add(" - Ya en 1.19.2: " + count[0] + " (" + ms(nanos[0]) + " ms)");
        lines.add(" - Antiguas desde la caché de ModernFix: " + count[1] + " (" + ms(nanos[1]) + " ms)");
        lines.add(" - Antiguas convertidas con DFU: " + count[2] + " (" + ms(nanos[2]) + " ms, de ellos " + ms(dfuOnly)
                + " ms convirtiendo; la primera conversión, que crea el DataFixerUpper: " + ms(firstDfu) + " ms)");
        return lines;
    }

    private static Path writeCsv() throws IOException {
        Path dir = FMLPaths.GAMEDIR.get().resolve("logs");
        Files.createDirectories(dir);
        Path file = dir.resolve("bteopt_structure_timing_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")) + ".csv");
        List<Entry> sorted = new ArrayList<>(ENTRIES);
        sorted.sort(Comparator.comparingLong(Entry::totalNanos).reversed());
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("estructura;dataversion_origen;tipo;ms_total;ms_dfu;primera_dfu;hilo\n");
            for (Entry e : sorted) {
                w.write(e.id() + ";" + e.dataVersion() + ";" + e.kind() + ";" + ms(e.totalNanos()).replace('.', ',') + ";"
                        + ms(e.dfuNanos()).replace('.', ',') + ";" + (e.firstDfu() ? "si" : "") + ";" + e.thread() + "\n");
            }
        }
        return file;
    }

    // ---- Comando y cierre ----

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("bteopt")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("nbtstats")
                        .executes(StructureLoadTiming::stats)
                        .then(Commands.literal("reset").executes(StructureLoadTiming::reset))));
    }

    private static int stats(CommandContext<CommandSourceStack> ctx) {
        for (String line : summaryLines()) {
            ctx.getSource().sendSuccess(Component.literal(line), false);
            BTEOptimizationFixes.LOGGER.info(line);
        }
        try {
            Path file = writeCsv();
            ctx.getSource().sendSuccess(Component.literal("[bteopt-nbt] Detalle en " + file.getFileName()), false);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("[bteopt-nbt] No se pudo escribir el CSV: " + e));
        }
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        ENTRIES.clear();
        ctx.getSource().sendSuccess(Component.literal("[bteopt-nbt] Contadores a cero"), false);
        return 1;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (ENTRIES.isEmpty()) return;
        summaryLines().forEach(BTEOptimizationFixes.LOGGER::info);
        try {
            BTEOptimizationFixes.LOGGER.info("[bteopt-nbt] Detalle en logs/{}", writeCsv().getFileName());
        } catch (IOException e) {
            BTEOptimizationFixes.LOGGER.warn("[bteopt-nbt] No se pudo escribir el CSV", e);
        }
    }
}
