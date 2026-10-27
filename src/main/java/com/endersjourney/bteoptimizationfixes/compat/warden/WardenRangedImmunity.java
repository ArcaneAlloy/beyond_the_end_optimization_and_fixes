package com.endersjourney.bteoptimizationfixes.compat.warden;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * El Warden no recibe dano de proyectiles (flechas, tridentes, virotes de
 * Dungeons Gear...) lanzados desde FUERA del alcance de su ataque sonico
 * (15 bloques en horizontal, 20 en vertical, como Warden#SonicBoom). Asi no
 * se le puede matar desde un agujero o una torre donde el no puede
 * responder; dentro de ese alcance se le sigue pudiendo disparar, porque
 * puede contestar con el ataque sonico.
 *
 * El proyectil rebota (igual que contra un Enderman) y al jugador se le
 * avisa en la barra de accion.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WardenRangedImmunity {

    /** Alcance horizontal del ataque sonico del Warden (bloques). */
    private static final double MAX_HORIZONTAL = 15.0D;
    /** Alcance vertical del ataque sonico del Warden (bloques). */
    private static final double MAX_VERTICAL = 20.0D;

    private WardenRangedImmunity() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Warden warden)) return;
        DamageSource source = event.getSource();
        if (!source.isProjectile()) return;

        Entity shooter = source.getEntity(); // quien disparo (null: dispensador, etc.)
        if (shooter != null && inSonicBoomRange(warden, shooter)) return;

        event.setCanceled(true);
        if (shooter instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("bte_optimization_fixes.warden.ranged_immune"), true);
        }
    }

    private static boolean inSonicBoomRange(Warden warden, Entity shooter) {
        double dx = shooter.getX() - warden.getX();
        double dz = shooter.getZ() - warden.getZ();
        double dy = shooter.getY() - warden.getY();
        return dx * dx + dz * dz <= MAX_HORIZONTAL * MAX_HORIZONTAL && Math.abs(dy) <= MAX_VERTICAL;
    }
}
