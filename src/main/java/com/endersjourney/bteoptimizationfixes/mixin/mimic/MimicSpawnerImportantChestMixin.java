package com.endersjourney.bteoptimizationfixes.mixin.mimic;

import com.endersjourney.bteoptimizationfixes.compat.mimic.ImportantChests;
import com.endersjourney.bteoptimizationfixes.compat.mimic.MimicTemplateData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mimic 1.2.38: el bloque marcador (SingleMimicSpawner) que se pone encima de un cofre al generar la estructura, en su
 * primer tick (m_213897_ = Block.tick) tira la probabilidad (RandomSource.nextInt(mimicSpawnChance) == 0) y, si toca,
 * crea un mimic que se come el cofre de debajo.
 *
 * - Si el cofre es importante (ImportantChests: Ender Eyes, objetos de mision...) se quita el marcador sin tirar: el
 *   cofre se queda como esta. Vale para todos los mimics, tambien los de las estructuras vanilla (p. ej. los cofres de
 *   la Desert Pyramid, donde End Remastered mete el Old Eye).
 * - Si el marcador es "forzado" (lo puso MimicTemplateChests, que ya eligio ese cofre con su propia probabilidad y
 *   maximo por estructura) la tirada del mod siempre sale: el mimic aparece seguro.
 *
 * Verificado por desensamblado de Mimic-forge-1.19.1-1.2.38.jar (una sola llamada a nextInt en ese metodo).
 */
@Pseudo
@Mixin(targets = "ca.lukegrahamlandry.mimic.blocks.SingleMimicSpawner", remap = false)
public abstract class MimicSpawnerImportantChestMixin {

    @Unique
    private static final ThreadLocal<Boolean> bteopt$FORCED = ThreadLocal.withInitial(() -> false);

    @Inject(method = "m_213897_", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$keepImportantChests(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        MimicTemplateData data = MimicTemplateData.get(level);
        boolean forced = data != null && data.consumeForced(pos.asLong());
        if (ImportantChests.isImportant(level.getServer(), level.getBlockEntity(pos.below()))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            ci.cancel();
            return;
        }
        bteopt$FORCED.set(forced);
    }

    @Redirect(
            method = "m_213897_",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;m_188503_(I)I", remap = false),
            remap = false
    )
    private int bteopt$forcedRoll(RandomSource random, int bound) {
        boolean forced = bteopt$FORCED.get();
        bteopt$FORCED.set(false);
        return forced ? 0 : random.nextInt(bound);
    }
}
