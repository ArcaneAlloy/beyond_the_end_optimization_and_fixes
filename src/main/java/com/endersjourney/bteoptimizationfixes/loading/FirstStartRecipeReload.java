package com.endersjourney.bteoptimizationfixes.loading;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import com.endersjourney.bteoptimizationfixes.loading.WorldLoadingStatus.Phase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((result, error) -> {
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
