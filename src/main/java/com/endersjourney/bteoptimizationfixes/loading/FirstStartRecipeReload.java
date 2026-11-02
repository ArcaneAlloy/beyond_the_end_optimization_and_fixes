package com.endersjourney.bteoptimizationfixes.loading;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import com.endersjourney.bteoptimizationfixes.loading.WorldLoadingStatus.Phase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Hace un /reload de datapacks la primera vez que arranca cada mundo.
 *
 * Problema: al crear un mundo nuevo en el modpack, la carga de recetas que
 * acaba usando el servidor no aplica los cambios de KubeJS (en el log:
 * "RecipeManagerMixin: RecipesEventJS.instance is null, falling back to
 * vanilla!"). Durante toda esa primera sesion faltan las recetas que anade
 * KubeJS (entre ellas las del altar de Summoning Rituals) y vuelven las que
 * elimina. Al salir y volver a entrar, o tras un /reload, todo es correcto.
 * Desactivar mixin.perf.kubejs y mixin.perf.skip_first_datapack_reload de
 * ModernFix no lo arreglo.
 *
 * Solucion: en ServerStartedEvent, si el mundo aun no tiene la marca
 * guardada, se recargan los datapacks seleccionados (lo mismo que hace
 * /reload) y, al terminar bien, se guarda la marca en el SavedData del
 * overworld para no repetirlo nunca mas en ese mundo. Ocurre todavia con la
 * pantalla de carga delante, antes de que el jugador pueda tocar nada.
 *
 * La recarga vacia la cache de plantillas de StructureTemplateManager, asi que
 * las 14 estructuras del lobby que Beyond The End acaba de precargar (~11 s)
 * se volvian a leer de disco en el primer tick (~13 s mas). Los datapacks son
 * los mismos antes y despues, asi que las plantillas tambien: se guarda la
 * cache antes de recargar y se devuelve al terminar.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FirstStartRecipeReload {

    private static final String DATA_NAME = BTEOptimizationFixes.MODID + "_first_start";

    private FirstStartRecipeReload() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        FirstStartData data = server.overworld().getDataStorage()
                .computeIfAbsent(FirstStartData::load, FirstStartData::new, DATA_NAME);
        if (data.recipesReloaded) {
            return;
        }

        BTEOptimizationFixes.LOGGER.info("[{}] Primer arranque del mundo: recargando datapacks para aplicar las recetas de KubeJS...",
                BTEOptimizationFixes.MODID);
        WorldLoadingStatus.set(Phase.RELOADING_RECIPES);
        long start = System.currentTimeMillis();

        Map<Object, Object> templates = templateCache(server.getStructureManager());
        Map<Object, Object> saved = templates == null ? null : new HashMap<>(templates);

        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((result, error) -> {
            if (saved != null && !saved.isEmpty()) {
                // putIfAbsent: si algo ya ha vuelto a cargar una plantilla tras la recarga, se respeta
                saved.forEach(templates::putIfAbsent);
                BTEOptimizationFixes.LOGGER.info("[{}] Conservadas {} plantillas de estructura tras la recarga",
                        BTEOptimizationFixes.MODID, saved.size());
            }
            if (error != null) {
                BTEOptimizationFixes.LOGGER.error("[{}] Fallo la recarga de datapacks del primer arranque; se reintentara la proxima vez",
                        BTEOptimizationFixes.MODID, error);
            } else {
                data.recipesReloaded = true;
                data.setDirty();
                BTEOptimizationFixes.LOGGER.info("[{}] Datapacks recargados en {} ms",
                        BTEOptimizationFixes.MODID, System.currentTimeMillis() - start);
            }
            WorldLoadingStatus.set(Phase.FINISHING);
        });
    }

    /**
     * Mapa interno de plantillas de StructureTemplateManager (structureRepository). Se busca por tipo porque es
     * el unico campo Map de la clase, sin depender del nombre SRG. null si no se encuentra: entonces no se toca nada.
     */
    @SuppressWarnings("unchecked")
    private static Map<Object, Object> templateCache(StructureTemplateManager manager) {
        try {
            Field found = null;
            for (Field f : StructureTemplateManager.class.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType()) || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (found != null) return null; // mas de un Map: no sabemos cual es
                found = f;
            }
            if (found == null) return null;
            found.setAccessible(true);
            return (Map<Object, Object>) found.get(manager);
        } catch (Throwable t) {
            BTEOptimizationFixes.LOGGER.warn("[{}] No se pudo acceder a la cache de plantillas de estructura",
                    BTEOptimizationFixes.MODID, t);
            return null;
        }
    }

    /** Marca persistente por mundo (data/bte_optimization_fixes_first_start.dat). */
    private static final class FirstStartData extends SavedData {

        private static final String KEY = "RecipesReloaded";

        private boolean recipesReloaded;

        private static FirstStartData load(CompoundTag tag) {
            FirstStartData data = new FirstStartData();
            data.recipesReloaded = tag.getBoolean(KEY);
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean(KEY, recipesReloaded);
            return tag;
        }
    }
}
