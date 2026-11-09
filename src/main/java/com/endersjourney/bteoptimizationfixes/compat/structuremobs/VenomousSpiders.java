package com.endersjourney.bteoptimizationfixes.compat.structuremobs;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Aranas de cueva "venenosas" de las estructuras del modpack (por ahora, los spawners de las Ancient Mines).
 *
 * El veneno de la arana de cueva esta escrito en el codigo de Minecraft (Veneno I, 7 s en Normal y 15 s en Dificil) y no
 * se puede cambiar desde el NBT. Las aranas de esos spawners llevan la etiqueta "ej_venomous" en su NBT (Tags), y al
 * morder a alguien se le aplica antes un veneno mas fuerte: Veneno II durante 10 s en Normal y 15 s en Dificil. El
 * Veneno I que pone despues Minecraft queda por debajo y no lo sustituye. En Pacifico y Facil no hacen nada, como en
 * vanilla. Las aranas de cueva normales del mundo no cambian.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VenomousSpiders {

    /** Etiqueta (Tags del NBT) que marca a las aranas con veneno reforzado. */
    public static final String TAG = "ej_venomous";

    private VenomousSpiders() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        Entity attacker = event.getSource().getDirectEntity();
        if (!(attacker instanceof CaveSpider spider) || !spider.getTags().contains(TAG)) return;
        LivingEntity target = event.getEntity();
        if (target.level.isClientSide()) return;
        Difficulty difficulty = target.level.getDifficulty();
        int seconds;
        if (difficulty == Difficulty.NORMAL) seconds = 10;
        else if (difficulty == Difficulty.HARD) seconds = 15;
        else return;
        target.addEffect(new MobEffectInstance(MobEffects.POISON, seconds * 20, 1), spider);
    }
}
