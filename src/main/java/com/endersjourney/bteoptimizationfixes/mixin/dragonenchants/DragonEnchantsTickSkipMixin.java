package com.endersjourney.bteoptimizationfixes.mixin.dragonenchants;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dragon Enchants 1.0.4: en cada tick de CADA entidad viva, DragonEnchantments.EnchantmentEvents.tickEvent calcula el
 * nivel de 4 encantamientos (End Step, Detonate, Homing, Wind Step) con EnchantmentHelper.getEnchantmentLevel, que
 * recorre su equipo y crea un EnumMap cada vez, y se lo pasa a su handler. Los 4 handlers no hacen nada con nivel 0.
 *
 * Una entidad sin nada equipado (la mayoría: animales, la mayoría de monstruos) tiene nivel 0 en todos, porque
 * getItemEnchantmentLevel devuelve 0 para un hueco vacío; así que se salta el evento entero sin calcular nada. Con
 * algo equipado se ejecuta como siempre. Sin Dragon Enchants el Mixin no se aplica (@Pseudo).
 */
@Pseudo
@Mixin(targets = "com.amuzil.omegasource.dragonenchants.enchants.DragonEnchantments$EnchantmentEvents", remap = false)
public abstract class DragonEnchantsTickSkipMixin {

    private static final EquipmentSlot[] ejfixes$SLOTS = EquipmentSlot.values();

    @Inject(method = "tickEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ejfixes$skipWithoutEquipment(LivingEvent.LivingTickEvent event, CallbackInfo ci) {
        LivingEntity entity = event.getEntity();
        for (EquipmentSlot slot : ejfixes$SLOTS) {
            if (!entity.getItemBySlot(slot).isEmpty()) return;
        }
        ci.cancel();
    }
}
