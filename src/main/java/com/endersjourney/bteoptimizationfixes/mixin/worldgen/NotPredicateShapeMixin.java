package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.worldgen.BlockPredicateShape;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Expone el predicado negado de "not" (ver worldgen/BlockPredicateShape). f_190506_ = predicate.
 * Nombre SRG sacado de las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.blockpredicates.NotPredicate", remap = false)
public abstract class NotPredicateShapeMixin implements BlockPredicateShape.Negated {

    @Shadow(remap = false)
    @Final
    private BlockPredicate f_190506_;

    @Override
    public BlockPredicate bteopt$inner() {
        return this.f_190506_;
    }
}
