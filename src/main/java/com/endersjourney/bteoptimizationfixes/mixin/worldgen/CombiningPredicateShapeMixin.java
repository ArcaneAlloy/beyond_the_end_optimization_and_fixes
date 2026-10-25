package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.worldgen.BlockPredicateShape;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

/**
 * Expone los hijos de all_of / any_of (ver worldgen/BlockPredicateShape). f_190453_ = predicates.
 * Nombre SRG sacado de las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.blockpredicates.CombiningPredicate", remap = false)
public abstract class CombiningPredicateShapeMixin implements BlockPredicateShape.Combining {

    @Shadow(remap = false)
    @Final
    protected List<BlockPredicate> f_190453_;

    @Override
    public List<BlockPredicate> bteopt$children() {
        return this.f_190453_;
    }
}
