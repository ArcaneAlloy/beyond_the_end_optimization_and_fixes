package com.endersjourney.bteoptimizationfixes.compat.boss;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Los bosses recuperan toda su vida cuando matan a un jugador. Antes conservaban la que les quedaba: el jugador podía
 * volver una y otra vez y bajarles la vida poco a poco hasta acabar con ellos.
 * <p>
 * Qué es un boss (no hay nada común entre mods):
 * <ul>
 *   <li>Cualquier entidad con barra de boss propia: un campo de un tipo que herede de BossEvent (vanilla Wither,
 *   Twilight Forest, Cataclysm con CMBossInfoServer, Meet Your Fight, Alex's Mobs, Mutant More, Dungeons Mobs...).
 *   Se mira una vez por clase y se guarda.</li>
 *   <li>Los bosses a los que Better Boss Bars les pone barra desde su config (betterbossbars.yml, sección bosses).
 *   Ojo: Better Boss Bars solo carga esa lista en el cliente (clientSetup), así que en un servidor dedicado está
 *   vacía; por eso los que dependen de ella (Warden, Warped Mosco, Ender Golem) también van en el tag.</li>
 *   <li>El tag bte_optimization_fixes:bosses, para los que llevan la barra fuera de la entidad (Ender Dragon, los de
 *   Beyond The End), los de Better Boss Bars y cualquier otro que se quiera añadir con un datapack.</li>
 * </ul>
 * Quién ha matado al jugador: el atacante del daño (también si fue con un proyectil, un hechizo o una parte del cuerpo
 * del boss) o, si murió por otra cosa (caída, vacío, lava...) justo después de que el boss le pegara, quien se lleva
 * la muerte según el registro de combate. Si un tótem le salva, no cuenta.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossHealOnPlayerKill {

    private static final Logger LOGGER = LoggerFactory.getLogger("BTEOptimizationFixes/BossHeal");
    public static final TagKey<EntityType<?>> BOSSES =
            TagKey.create(Registry.ENTITY_TYPE_REGISTRY, new ResourceLocation(BTEOptimizationFixes.MODID, "bosses"));

    /** ¿La clase tiene barra de boss propia? (por clase, se calcula una vez) */
    private static final Map<Class<?>, Boolean> HAS_BOSS_BAR = new ConcurrentHashMap<>();

    private static boolean betterBossBarsLooked = false;
    private static Method betterBossBarsGetBosses = null;

    private BossHealOnPlayerKill() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;   // un tótem (u otro mod) le ha salvado
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;

        LivingEntity boss = killer(event.getSource().getEntity());
        if (boss == null) boss = killer(event.getSource().getDirectEntity());
        if (boss == null) boss = player.getKillCredit();
        if (boss == null || boss == player || !boss.isAlive() || !isBoss(boss)) return;

        if (boss.getHealth() < boss.getMaxHealth()) {
            boss.setHealth(boss.getMaxHealth());
            LOGGER.debug("{} killed {}: healed to full", boss.getType(), player.getGameProfile().getName());
        }
    }

    /** El ser vivo detrás del daño: la propia entidad o, si es una parte del cuerpo (Ender Dragon...), su dueño. */
    private static LivingEntity killer(Entity entity) {
        if (entity instanceof PartEntity<?> part) entity = part.getParent();
        return entity instanceof LivingEntity living ? living : null;
    }

    public static boolean isBoss(LivingEntity entity) {
        if (entity.getType().is(BOSSES)) return true;
        if (HAS_BOSS_BAR.computeIfAbsent(entity.getClass(), BossHealOnPlayerKill::hasBossBarField)) return true;
        return isBetterBossBarsBoss(entity.getType());
    }

    /** ¿Alguna clase de su jerarquía (sin contar las de Minecraft de base) declara un campo de barra de boss? */
    private static boolean hasBossBarField(Class<?> clazz) {
        for (Class<?> c = clazz; c != null && c != LivingEntity.class && c != Entity.class && c != Object.class; c = c.getSuperclass()) {
            try {
                for (Field f : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) && BossEvent.class.isAssignableFrom(f.getType())) return true;
                }
            } catch (Throwable t) {
                // Una clase que no se puede inspeccionar (falta una dependencia opcional...): se pasa a la siguiente
            }
        }
        return false;
    }

    /** Bosses de la config de Better Boss Bars (BetterBossBars.getBosses()), si está instalado. */
    private static boolean isBetterBossBarsBoss(EntityType<?> type) {
        if (!betterBossBarsLooked) {
            betterBossBarsLooked = true;
            try {
                betterBossBarsGetBosses = Class.forName("com.gmail.picono435.betterbossbars.BetterBossBars").getMethod("getBosses");
            } catch (Throwable t) {
                betterBossBarsGetBosses = null;
            }
        }
        if (betterBossBarsGetBosses == null) return false;
        try {
            Object map = betterBossBarsGetBosses.invoke(null);
            return map instanceof Map<?, ?> m && m.containsKey(type);
        } catch (Throwable t) {
            return false;
        }
    }
}
