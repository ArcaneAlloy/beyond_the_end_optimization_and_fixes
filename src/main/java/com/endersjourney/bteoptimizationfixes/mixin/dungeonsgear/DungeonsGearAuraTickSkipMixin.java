package com.endersjourney.bteoptimizationfixes.mixin.dungeonsgear;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dungeons Gear 5.0.6: cada uno de estos encantamientos de pantalón tiene su propio evento que se ejecuta en CADA tick
 * de CADA entidad viva (onLivingUpdate). Todos hacen lo mismo: comprobar canEnchantmentTrigger y luego, en
 * triggerEffect, mirar con EnchantmentHelper.getEnchantmentLevel (directamente o con ModEnchantmentHelper
 * .hasEnchantment, que es "nivel > 0") si la entidad lleva el encantamiento; si no lo lleva, no hacen nada más.
 * Juntos con Cowardice eran el ~6,7% del tick del servidor en la prueba de partida.
 *
 * Una entidad sin nada equipado tiene nivel 0 en todo (getItemEnchantmentLevel devuelve 0 para un hueco vacío), así
 * que para ella se salta el evento sin calcular nada. Con algo equipado se ejecuta como siempre.
 *
 * Cowardice no se toca: además de mirar el encantamiento, quita en cada tick un modificador de atributos, y
 * saltárselo podría dejar ese modificador puesto en alguien que se quitó el equipo.
 * Sin Dungeons Gear el Mixin no se aplica (@Pseudo).
 */
@Pseudo
@Mixin(targets = {
        "com.infamous.dungeons_gear.enchantments.armor.legs.ChillingEnchantment",
        "com.infamous.dungeons_gear.enchantments.armor.legs.GravityPulseEnchantment",
        "com.infamous.dungeons_gear.enchantments.armor.legs.SnowballEnchantment",
        "com.infamous.dungeons_gear.enchantments.armor.legs.MeleeAuraEnchantment",
        "com.infamous.dungeons_gear.enchantments.armor.legs.BurningEnchantment",
        "com.infamous.dungeons_gear.enchantments.armor.legs.LifeStealAuraEnchantment",
        "com.infamous.dungeons_gear.enchantments.armor.legs.SpeedAuraEnchantment"
}, remap = false)
public abstract class DungeonsGearAuraTickSkipMixin {

    private static final EquipmentSlot[] ejfixes$SLOTS = EquipmentSlot.values();

    @Inject(method = "onLivingUpdate", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ejfixes$skipWithoutEquipment(LivingEvent.LivingTickEvent event, CallbackInfo ci) {
        LivingEntity entity = event.getEntity();
        for (EquipmentSlot slot : ejfixes$SLOTS) {
            if (!entity.getItemBySlot(slot).isEmpty()) return;
        }
        ci.cancel();
    }
}
