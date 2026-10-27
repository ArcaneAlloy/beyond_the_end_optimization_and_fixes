package com.endersjourney.bteoptimizationfixes.compat.cataclysm;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Cataclysm: el Cangrejo de Amatista (cataclysm:amethyst_crab) aparece de forma
 * natural en las Cuevas Frondosas (y, con GeodeCrabSpawner, dentro de geodas).
 * Para poder subirle la probabilidad sin que salgan varios en la misma zona,
 * se cancela su spawn NATURAL si ya hay otro cangrejo cerca.
 *
 * Solo afecta al spawn natural: /summon, huevos, spawners, etc. siguen igual.
 * Se ejecuta con prioridad LOWEST para que ningun otro mod (In Control...)
 * vuelva a permitirlo despues.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AmethystCrabSpawnLimit {

    private static final ResourceLocation CRAB_ID = new ResourceLocation("cataclysm", "amethyst_crab");

    /** Distancia horizontal (bloques) dentro de la que no puede haber otro cangrejo. */
    private static final double RADIUS_XZ = 96.0D;
    /** Distancia vertical (bloques) hacia arriba y hacia abajo. */
    private static final double RADIUS_Y = 64.0D;

    private static EntityType<?> crabType;
    private static boolean lookedUp = false;

    private AmethystCrabSpawnLimit() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.getSpawnReason() != MobSpawnType.NATURAL) return;

        EntityType<?> crab = getCrabType();
        if (crab == null || event.getEntity().getType() != crab) return;
        if (event.getResult() == Event.Result.DENY) return;

        if (hasCrabNearby(event.getLevel(), event.getEntity(), event.getX(), event.getY(), event.getZ())) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** ¿Hay otro cangrejo vivo (distinto de {@code except}) cerca de esa posicion? */
    public static boolean hasCrabNearby(LevelAccessor level, @Nullable Entity except, double x, double y, double z) {
        EntityType<?> crab = getCrabType();
        if (crab == null) return false;
        AABB area = new AABB(x - RADIUS_XZ, y - RADIUS_Y, z - RADIUS_XZ, x + RADIUS_XZ, y + RADIUS_Y, z + RADIUS_XZ);
        return !level.getEntities(except, area, e -> e.getType() == crab && e.isAlive()).isEmpty();
    }

    @Nullable
    public static EntityType<?> getCrabType() {
        if (!lookedUp) {
            lookedUp = true;
            // getValue devuelve el tipo por defecto (cerdo) si no existe: comprobar antes
            if (ForgeRegistries.ENTITY_TYPES.containsKey(CRAB_ID)) {
                crabType = ForgeRegistries.ENTITY_TYPES.getValue(CRAB_ID);
            }
        }
        return crabType;
    }
}
