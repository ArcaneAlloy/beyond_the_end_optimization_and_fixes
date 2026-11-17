package com.endersjourney.bteoptimizationfixes.mixin.item;

import com.endersjourney.bteoptimizationfixes.compat.dungeonsgear.ItemMaxDamageAccess;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Hace que Item implemente ItemMaxDamageAccess para poder cambiar su durabilidad maxima (la usa UnbreakableArtifacts).
 * f_41371_ = maxDamage, nombre SRG sacado de las tablas de mapeo de Forge 1.19.2. Con @Shadow + @Mutable, como el resto
 * de mixins de este mod: @Accessor no sirve aqui porque el procesador de anotaciones lo comprueba contra los nombres
 * de desarrollo y el build falla.
 */
@Mixin(value = Item.class, remap = false)
public abstract class ItemMaxDamageAccessor implements ItemMaxDamageAccess {

    @Shadow(remap = false)
    @Final
    @Mutable
    private int f_41371_;

    @Override
    public void bteopt$setMaxDamage(int maxDamage) {
        this.f_41371_ = maxDamage;
    }
}
