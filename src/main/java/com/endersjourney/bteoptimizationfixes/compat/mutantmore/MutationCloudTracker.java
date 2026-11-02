package com.endersjourney.bteoptimizationfixes.compat.mutantmore;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Nubes de mutación de Mutant More (com.alexander.mutantmore.entities.MutationCloud) que hay ahora mismo en cada
 * nivel, cliente y servidor por separado.
 *
 * MiscUtils.mutationCloudForMob(mob) busca, para CADA entidad viva y en CADA tick (y en cada frame al dibujarla), las
 * nubes que tocan a esa entidad, para ver si alguna la está mutando. Casi nunca hay ninguna nube en el nivel, y esa
 * búsqueda era el ~2,5% del tick del servidor en la prueba de partida. Con este registro, MutationCloudSkipMixin
 * devuelve null directamente cuando el nivel no tiene ninguna nube, que es justo lo que habría devuelto la búsqueda.
 *
 * Se lleva con los eventos de Forge de entrar y salir del nivel (también al cargar y descargar chunks).
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MutationCloudTracker {

    private static final String CLOUD_CLASS = "com.alexander.mutantmore.entities.MutationCloud";

    private static final Map<Level, Set<Entity>> CLOUDS = new WeakHashMap<>();

    private MutationCloudTracker() {
    }

    private static boolean isCloud(Entity entity) {
        for (Class<?> c = entity.getClass(); c != null && c != Entity.class; c = c.getSuperclass()) {
            if (c.getName().equals(CLOUD_CLASS)) return true;
        }
        return false;
    }

    /** true si en este nivel no hay ninguna nube de mutación (la búsqueda no encontraría nada). */
    public static boolean noClouds(Level level) {
        synchronized (CLOUDS) {
            Set<Entity> set = CLOUDS.get(level);
            return set == null || set.isEmpty();
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!isCloud(event.getEntity())) return;
        synchronized (CLOUDS) {
            CLOUDS.computeIfAbsent(event.getLevel(), l -> Collections.newSetFromMap(new IdentityHashMap<>())).add(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!isCloud(event.getEntity())) return;
        synchronized (CLOUDS) {
            Set<Entity> set = CLOUDS.get(event.getLevel());
            if (set != null) set.remove(event.getEntity());
        }
    }
}
