package com.endersjourney.bteoptimizationfixes.mixin.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Objetos iguales que no se apilan: algunos lingotes de oro, esmeraldas, flechas... llevan un NBT {Damage: 0} y por eso
 * ya no se juntan con los normales (para apilar, el NBT tiene que ser identico; tener NBT y no tenerlo cuenta como
 * distinto aunque sea {Damage: 0} o un NBT vacio).
 *
 * Causa: en 1.19.2 ItemStack.setDamageValue (m_41721_) escribe "Damage" en el NBT de CUALQUIER objeto, aunque no tenga
 * durabilidad. Basta con que un mod llame a setDamageValue(0) sobre un objeto cualquiera (p. ej. "reparar" todo lo que
 * hay en un inventario) para que el objeto quede marcado para siempre.
 *
 * Arreglo, solo para objetos SIN durabilidad (las herramientas y armaduras no cambian):
 * - setDamageValue(0) ya no crea NBT. Si ya tenia "Damage", se quita (0 es el valor por defecto, asi que leerlo sigue
 *   dando 0).
 * - Al cargar un objeto (mundo, jugador, cofres, almacenes de mods...) se quita el "Damage: 0" que tuviera, y si el NBT
 *   se queda vacio se borra. Asi los objetos que ya estaban marcados en un mundo existente vuelven a apilarse.
 *
 * Mapeo verificado con server-1.19.2-20220805.130853-srg.jar: f_41590_ = tag, m_41721_ = setDamageValue,
 * <init>(CompoundTag) = ItemStack(CompoundTag), usado por ItemStack.of al cargar.
 */
@Mixin(value = ItemStack.class, remap = false)
public abstract class ItemStackDamageTagMixin {

    @Shadow(remap = false)
    private CompoundTag f_41590_;

    @Unique
    private boolean bteopt$hasNoDurability() {
        ItemStack self = (ItemStack) (Object) this;
        return !self.isEmpty() && !self.getItem().isDamageable(self);
    }

    @Inject(method = "m_41721_", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$skipZeroDamageOnUndamageable(int damage, CallbackInfo ci) {
        if (damage != 0 || !bteopt$hasNoDurability()) return;
        if (this.f_41590_ != null) this.f_41590_.remove("Damage");
        ci.cancel();
    }

    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"), remap = false)
    private void bteopt$cleanLoadedTag(CompoundTag saved, CallbackInfo ci) {
        CompoundTag tag = this.f_41590_;
        if (tag == null) return;
        if (tag.contains("Damage", Tag.TAG_ANY_NUMERIC) && tag.getInt("Damage") == 0 && bteopt$hasNoDurability()) {
            tag.remove("Damage");
        }
        // Un NBT vacio no aporta nada y tambien impide apilar con los objetos sin NBT
        if (tag.isEmpty()) this.f_41590_ = null;
    }
}
