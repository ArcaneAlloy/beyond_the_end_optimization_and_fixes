package com.endersjourney.bteoptimizationfixes.compat.dungeonsmobs;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dungeons Mobs 4.0.8: las aranas que disparan telarana fallan siempre cuando estan subidas a una pared o al techo.
 *
 * Causa: IWebShooter.shootWeb crea el proyectil 1 bloque por encima de los pies de la arana y 0,75 bloques hacia un
 * lado (segun hacia donde mira el cuerpo). Pegada al techo o a una esquina, ese punto cae dentro del bloque: el
 * proyectil choca con el nada mas salir (pone la telarana ahi mismo) o se borra por estar dentro de un bloque, asi que
 * nunca llega al jugador.
 *
 * Arreglo: cuando aparece uno de esos proyectiles, si el punto de salida esta dentro de un bloque o hay un bloque entre
 * ese punto y el objetivo, se mueve a la parte delantera del cuerpo de la arana, en direccion al objetivo, y se vuelve a
 * apuntar con la misma formula que usa Dungeons Mobs (velocidad 1, imprecision 5, compensando la caida). Si el disparo
 * normal ya tenia camino libre, no se toca.
 *
 * Se identifica el proyectil por el nombre de su clase (no hace falta tener Dungeons Mobs para compilar ni para
 * arrancar). Verificado por desensamblado de dungeons_mobs-1.19.2-4.0.8-beta.jar (IWebShooter.shootWeb,
 * CobwebProjectileEntity.tick).
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpiderWebAim {

    private static final String COBWEB_PROJECTILE = "com.infamous.dungeons_mobs.entities.projectiles.CobwebProjectileEntity";

    private SpiderWebAim() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk()) return;
        Level level = event.getLevel();
        if (level.isClientSide()) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof Projectile web) || !COBWEB_PROJECTILE.equals(entity.getClass().getName())) return;
        if (!(web.getOwner() instanceof Mob shooter)) return;
        LivingEntity target = shooter.getTarget();
        if (target == null || !target.isAlive()) return;

        Vec3 aimPoint = new Vec3(target.getX(), target.getY(0.3333333333333333D), target.getZ());
        Vec3 spawn = web.position();
        if (isFree(level, web, spawn) && hasClearPath(level, web, spawn, aimPoint)) return;

        // Desde el centro del cuerpo de la arana, un poco por delante en direccion al objetivo
        Vec3 center = new Vec3(shooter.getX(), shooter.getY(0.5D), shooter.getZ());
        Vec3 dir = aimPoint.subtract(center);
        if (dir.lengthSqr() < 1.0E-4D) return;
        Vec3 front = center.add(dir.normalize().scale(shooter.getBbWidth() * 0.5D + 0.3D));
        Vec3 newSpawn = isFree(level, web, front) ? front : center;
        web.setPos(newSpawn.x, newSpawn.y, newSpawn.z);

        // Misma punteria que IWebShooter.shootWeb: compensa la caida del proyectil con 0,2 por bloque de distancia
        double dx = target.getX() - newSpawn.x;
        double dy = target.getY(0.3333333333333333D) - newSpawn.y;
        double dz = target.getZ() - newSpawn.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        web.shoot(dx, dy + horizontal * 0.2D, dz, 1.0F, 5.0F);
    }

    /** El proyectil cabe en ese punto sin chocar con bloques. */
    private static boolean isFree(Level level, Entity web, Vec3 pos) {
        AABB box = web.getBoundingBox().move(pos.subtract(web.position()));
        return level.noCollision(web, box);
    }

    /** No hay ningun bloque entre el punto de salida y el objetivo. */
    private static boolean hasClearPath(Level level, Entity web, Vec3 from, Vec3 to) {
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, web));
        return hit.getType() == HitResult.Type.MISS;
    }
}
