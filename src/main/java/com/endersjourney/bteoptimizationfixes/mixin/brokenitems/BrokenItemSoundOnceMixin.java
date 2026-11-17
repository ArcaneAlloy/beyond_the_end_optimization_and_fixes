package com.endersjourney.bteoptimizationfixes.mixin.brokenitems;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Don't Break Items 1.19.2-1.19.4-1.3 (brokenitems): en vez de romperse, el objeto se queda "roto" con 1 de durabilidad
 * (desgaste = maxDamage - 1; su ItemStackMixin sobrescribe hurtAndBreak). Pero cada vez que un objeto ya roto se vuelve
 * a gastar (la armadura en cada golpe que recibes, por ejemplo), hurtAndBreak llega otra vez al máximo, suena la rotura
 * y lo devuelve a maxDamage - 1: suena en cada golpe.
 *
 * Arreglo: el sonido de brokenitems se quita y en su lugar suena una sola vez, cuando el objeto pasa de sano a roto
 * (antes del golpe le quedaba más de 1 de durabilidad y después ya no). Mismo sonido, volumen y tono que el de
 * brokenitems. Si se repara y se vuelve a romper, vuelve a sonar.
 *
 * Prioridad 1500: se aplica después de la sobrescritura de brokenitems (prioridad 1000), así que el Redirect encuentra su
 * playSound. Sin brokenitems no hace nada: el Redirect no encuentra la llamada (require = 0) y el sonido nuevo solo se
 * usa si el mod está cargado.
 *
 * Verificado por desensamblado de dont_break_items-1.19.2-1.19.4-1.3.jar (ItemStackMixin: m_41622_ llama a
 * Level.m_6263_ con SoundEvents.ITEM_BREAK, PLAYERS, 0.8, 0.8 + random * 0.4; BrokenItemsEvents.isItemBroken: maxDamage
 * > 1 y desgaste >= maxDamage - 1) y forge-1.19.2-43.5.2: m_41622_ = hurtAndBreak, m_41773_ = getDamageValue,
 * m_41776_ = getMaxDamage, m_6263_ = Level.playSound(Player, double, double, double, SoundEvent, SoundSource, float,
 * float).
 */
@Mixin(value = ItemStack.class, priority = 1500, remap = false)
public abstract class BrokenItemSoundOnceMixin {

    @Unique
    private static Boolean bteopt$brokenItemsLoaded;

    @Shadow(remap = false)
    public abstract int m_41773_();

    @Shadow(remap = false)
    public abstract int m_41776_();

    /** Desgaste que tenía el objeto al empezar este hurtAndBreak. */
    @Unique
    private int bteopt$damageBeforeHurt = -1;

    @Unique
    private static boolean bteopt$brokenItems() {
        if (bteopt$brokenItemsLoaded == null) bteopt$brokenItemsLoaded = ModList.get().isLoaded("brokenitems");
        return bteopt$brokenItemsLoaded;
    }

    @Inject(method = "m_41622_", at = @At("HEAD"), remap = false)
    private <T extends LivingEntity> void bteopt$rememberDamage(int amount, T entity, Consumer<T> onBroken, CallbackInfo ci) {
        this.bteopt$damageBeforeHurt = this.m_41773_();
    }

    /** El sonido de brokenitems, que suena en cada golpe a un objeto ya roto: fuera. */
    @Redirect(
            method = "m_41622_",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;m_6263_(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
                    remap = false),
            require = 0,
            remap = false)
    private void bteopt$skipRepeatedBreakSound(Level level, Player player, double x, double y, double z,
                                               SoundEvent sound, SoundSource source, float volume, float pitch) {
    }

    /** Una sola vez: cuando este golpe lo ha dejado roto y antes no lo estaba. */
    @Inject(method = "m_41622_", at = @At("TAIL"), remap = false)
    private <T extends LivingEntity> void bteopt$breakSoundOnce(int amount, T entity, Consumer<T> onBroken, CallbackInfo ci) {
        if (entity == null || !bteopt$brokenItems()) return;
        int max = this.m_41776_();
        if (max <= 1) return;
        boolean wasBroken = this.bteopt$damageBeforeHurt >= max - 1;
        boolean isBroken = this.m_41773_() >= max - 1;
        if (wasBroken || !isBroken) return;
        Level level = entity.level;
        if (level.isClientSide) return;
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS,
                0.8F, 0.8F + level.random.nextFloat() * 0.4F);
    }
}
