package com.endersjourney.bteoptimizationfixes.mixin.item;

import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Acceso a la durabilidad maxima de un objeto (Item.maxDamage, final). Lo usa UnbreakableArtifacts.
 * <p>
 * Mapeo verificado con forge-1.19.2-43.5.2: f_41371_ = maxDamage.
 */
@Mixin(value = Item.class, remap = false)
public interface ItemMaxDamageAccessor {

    @Mutable
    @Accessor(value = "f_41371_", remap = false)
    void setBteoptMaxDamage(int maxDamage);
}
