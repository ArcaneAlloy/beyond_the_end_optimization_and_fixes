package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.worldgen.BlockPredicateShape;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.blockpredicates.StateTestingPredicate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Expone el offset de las comprobaciones de estado (ver worldgen/BlockPredicateShape). f_190539_ = offset.
 * Nombre SRG sacado de las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(value = StateTestingPredicate.class, remap = false)
public abstract class StateTestingPredicateShapeMixin implements BlockPredicateShape.StateLeaf {

    @Shadow(remap = false)
    @Final
    protected Vec3i f_190539_;

    @Override
    public Vec3i bteopt$offset() {
        return this.f_190539_;
    }
}
