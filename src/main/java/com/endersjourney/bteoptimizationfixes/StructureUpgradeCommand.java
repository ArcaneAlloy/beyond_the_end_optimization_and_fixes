package com.endersjourney.bteoptimizationfixes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Herramienta de un solo uso: "/bteopt upgradestructures <carpeta_entrada> <carpeta_salida>"
 *
 * Convierte todos los .nbt de estructura bajo <carpeta_entrada> (recursivo)
 * a la version de datos ACTUAL, usando el DataFixerUpper real del propio
 * servidor -- el mismo camino que sigue el juego cuando carga una
 * estructura antigua en caliente (CachingStructureManager/
 * StructureTemplateManager en tiempo de ejecucion), solo que aqui lo
 * hacemos nosotros una vez, offline, y guardamos el resultado ya
 * actualizado en <carpeta_salida> (misma estructura de subcarpetas que la
 * entrada). Los ficheros de <carpeta_salida> son los que luego hay que
 * meter de vuelta en el jar del mod / datapack, sustituyendo a los
 * originales.
 *
 * Por que asi y no a mano: convertir de una version de datos antigua a la
 * actual implica ejecutar el DataFixerUpper de Mojang, que es un sistema
 * enorme y con muchas transformaciones encadenadas segun la version de
 * origen -- reimplementarlo a mano se arriesga a corromper estructuras.
 * Dejar que el propio juego lo haga (que es exactamente lo que hace solo,
 * en caliente, cada vez que carga uno de estos ficheros) es la unica forma
 * fiable de hacerlo bien.
 *
 * Uso: mete el addon en mods/, copia los .nbt que quieras convertir a una
 * carpeta (ej. "para_convertir/betterdungeons/..."), arranca un mundo
 * cualquiera, y en la consola/chat (con permisos de operador) ejecuta:
 *   /bteopt upgradestructures para_convertir salida
 * Cuando termine, salida/ contiene los mismos ficheros ya en DataVersion
 * actual, listos para reemplazar a los originales en el jar/datapack.
 *
 * AVISO: StructureTemplate.load(CompoundTag) y .save(CompoundTag) estan
 * verificados por descompilacion directa del jar real de Forge 43.5.2
 * (1.19.2). NbtUtils.update(DataFixer, DataFixTypes, CompoundTag, int) esta
 * verificado cruzando la documentacion oficial de Forge para 1.18.2 y
 * 1.19.3 (ambas muestran que DataFixTypes no tiene metodos update propios
 * en esta franja de versiones -- viven en NbtUtils). No verificado por
 * descompilacion directa de tu jar exacto, pero con buena confianza al
 * coincidir en las dos versiones que rodean a la tuya. Compilalo y
 * pruebalo con un puñado de ficheros antes de lanzarlo sobre las
 * estructuras de golpe.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StructureUpgradeCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("bteopt")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("upgradestructures")
                                .then(Commands.argument("inputDir", StringArgumentType.string())
                                        .then(Commands.argument("outputDir", StringArgumentType.string())
                                                .executes(StructureUpgradeCommand::run))))
        );
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        String inputDirArg = StringArgumentType.getString(ctx, "inputDir");
        String outputDirArg = StringArgumentType.getString(ctx, "outputDir");
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        Path inputDir = Paths.get(inputDirArg);
        Path outputDir = Paths.get(outputDirArg);

        if (!Files.isDirectory(inputDir)) {
            source.sendFailure(Component.literal("[bteopt] La carpeta de entrada no existe: " + inputDir.toAbsolutePath()));
            return 0;
        }

        List<Path> nbtFiles;
        try (Stream<Path> walk = Files.walk(inputDir)) {
            nbtFiles = walk.filter(p -> p.toString().toLowerCase().endsWith(".nbt"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            source.sendFailure(Component.literal("[bteopt] Error listando " + inputDir + ": " + e));
            return 0;
        }

        source.sendSuccess(Component.literal("[bteopt] Encontrados " + nbtFiles.size() + " ficheros .nbt. Convirtiendo..."), false);
        BTEOptimizationFixes.LOGGER.info("[bteopt] upgradestructures: {} ficheros bajo {}", nbtFiles.size(), inputDir);

        int ok = 0;
        int alreadyCurrent = 0;
        int failed = 0;

        for (Path in : nbtFiles) {
            Path relative = inputDir.relativize(in);
            Path out = outputDir.resolve(relative);
            try {
                CompoundTag rawTag;
                try (InputStream is = Files.newInputStream(in)) {
                    rawTag = NbtIo.readCompressed(is);
                }

                int oldVersion = rawTag.contains("DataVersion", 99) ? rawTag.getInt("DataVersion") : 500;
                // SharedConstants.getCurrentVersion().getWorldVersion() -- exactamente el mismo
                // patron que usa StructureTemplate.save() internamente (verificado por
                // descompilacion directa: SharedConstants.m_183709_().getWorldVersion()).
                int currentVersion = net.minecraft.SharedConstants.getCurrentVersion().getWorldVersion();

                if (oldVersion >= currentVersion) {
                    alreadyCurrent++;
                    // Aun asi la copiamos a la salida, para que la carpeta
                    // de salida quede completa y lista para reempaquetar.
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    continue;
                }

                // DataFixTypes NO tiene metodos update()/updateToCurrentVersion() propios
                // en esta version -- confirmado comparando la documentacion oficial de
                // Forge generada para 1.18.2 y 1.19.3 (justo a los dos lados de 1.19.2):
                // DataFixTypes.html solo lista values()/valueOf()/getType(). Los metodos
                // de conversion viven en NbtUtils como estaticos, recibiendo el
                // DataFixTypes como parametro -- al reves de como lo tenia yo las dos
                // veces anteriores (de ahi los dos fallos de compilacion previos).
                CompoundTag fixedTag = NbtUtils.update(
                        server.getFixerUpper(), DataFixTypes.STRUCTURE, rawTag, oldVersion);

                StructureTemplate template = new StructureTemplate();
                template.load(fixedTag);

                CompoundTag outTag = template.save(new CompoundTag());

                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out)) {
                    NbtIo.writeCompressed(outTag, os);
                }

                ok++;
            } catch (Exception e) {
                failed++;
                BTEOptimizationFixes.LOGGER.error("[bteopt] Fallo convirtiendo {}: {}", in, e.toString());
            }
        }

        final int okF = ok, alreadyF = alreadyCurrent, failedF = failed;
        source.sendSuccess(Component.literal(String.format(
                "[bteopt] Listo. Convertidas: %d | Ya actuales (copiadas tal cual): %d | Fallidas: %d | Salida: %s",
                okF, alreadyF, failedF, outputDir.toAbsolutePath()
        )), false);
        BTEOptimizationFixes.LOGGER.info("[bteopt] upgradestructures terminado. ok={} yaActuales={} fallidas={}", okF, alreadyF, failedF);

        return ok;
    }
}
