package com.endersjourney.bteoptimizationfixes.mixin.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Objetos iguales que no se apilan: algunos lingotes de oro, esmeraldas, flechas... llevan un NBT {Damage: 0} y por eso
 * ya no se juntan con los normales (para apilar, el NBT tiene que ser identico; tener NBT y no tenerlo cuenta como
 * distinto aunque sea {Damage: 0} o un NBT vacio).
 *
 * En el juego se ve al craftear con mayusculas + clic en la matriz de crafteo del almacen de Occultism (p. ej. esmeraldas
 * a partir de bloques de esmeralda). Occultism monta el resultado con new ItemStack(item, 0) + setTag(resultado.getTag())
 * y lo da al jugador, asi que el NBT del resultado pasa tal cual.
 *
 * Arreglo, solo para objetos SIN durabilidad (las herramientas y armaduras no cambian). Se quita el "Damage: 0" (0 es el
 * valor por defecto, asi que leerlo sigue dando 0) y, si el NBT se queda vacio, se borra:
 * - setDamageValue(0) ya no crea NBT (en 1.19.2 escribe "Damage" en cualquier objeto, aunque no tenga durabilidad).
 * - setTag(...): cubre el camino de Occultism y cualquier otro que copie un NBT con "Damage: 0".
 * - copy(): cubre los resultados de las recetas (assemble devuelve una copia del resultado).
 * - Al cargar un objeto (mundo, jugador, cofres, almacenes de mods...): los objetos que ya estaban marcados en un mundo
 *   existente vuelven a apilarse.
 *
 * Mapeo verificado con forge-1.19.2-43.5.2-server.jar: f_41589_ = item, f_41590_ = tag, m_41721_ = setDamageValue,
 * m_41751_ = setTag, m_41777_ = copy, <init>(CompoundTag) = ItemStack(CompoundTag), usado por ItemStack.of al cargar.
 */
@Mixin(value = ItemStack.class, remap = false)
public abstract class ItemStackDamageTagMixin {

    @Shadow(remap = false)
    private CompoundTag f_41590_;

    @Shadow(remap = false)
    @Final
    private Item f_41589_;

    /**
     * Se mira el campo del objeto y no getItem(): Occultism pone el NBT cuando la cantidad aun es 0, y con cantidad 0
     * getItem() devuelve aire.
     */
    @Unique
    private boolean bteopt$hasNoDurability() {
        Item item = this.f_41589_;
        return item != null && item != Items.AIR && !item.isDamageable((ItemStack) (Object) this);
    }

    /** Quita "Damage: 0" de un objeto sin durabilidad; si el NBT se queda vacio, lo borra. */
    @Unique
    private void bteopt$cleanDamageTag() {
        CompoundTag tag = this.f_41590_;
        if (tag == null || !tag.contains("Damage", Tag.TAG_ANY_NUMERIC) || tag.getInt("Damage") != 0) return;
        if (!bteopt$hasNoDurability()) return;
        tag.remove("Damage");
        if (tag.isEmpty()) this.f_41590_ = null;
    }

    @Inject(method = "m_41721_", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$skipZeroDamageOnUndamageable(int damage, CallbackInfo ci) {
        if (damage != 0 || !bteopt$hasNoDurability()) return;
        if (this.f_41590_ != null) this.f_41590_.remove("Damage");
        ci.cancel();
    }

    @Inject(method = "m_41751_", at = @At("RETURN"), remap = false)
    private void bteopt$cleanSetTag(CompoundTag tag, CallbackInfo ci) {
        bteopt$cleanDamageTag();
    }

    @Inject(method = "m_41777_", at = @At("RETURN"), remap = false)
    private void bteopt$cleanCopy(CallbackInfoReturnable<ItemStack> cir) {
        ((ItemStackDamageTagMixin) (Object) cir.getReturnValue()).bteopt$cleanDamageTag();
    }

    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"), remap = false)
    private void bteopt$cleanLoadedTag(CompoundTag saved, CallbackInfo ci) {
        bteopt$cleanDamageTag();
        // Un NBT vacio no aporta nada y tambien impide apilar con los objetos sin NBT
        if (this.f_41590_ != null && this.f_41590_.isEmpty()) this.f_41590_ = null;
    }
}
