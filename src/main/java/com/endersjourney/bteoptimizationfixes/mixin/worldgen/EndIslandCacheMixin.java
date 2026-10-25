package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.worldgen.PureValueCaches;
import com.endersjourney.bteoptimizationfixes.worldgen.WorldgenOpt;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Optimización exacta (ver WorldgenOpt, punto 4): la función "minecraft:end_islands" calcula la altura de las islas
 * del End recorriendo una rejilla de 25x25 puntos de ruido simplex, y solo depende de (x / 8, z / 8). Nullscape la
 * usa sin caché, así que se recalcula para muchísimos puntos de la misma columna. Aquí se guarda el resultado por
 * (x / 8, z / 8) y la cuenta final es la misma de vanilla.
 * m_207386_ = compute(FunctionContext), m_224062_ = getHeightValue(SimplexNoise, int, int), f_208627_ = islandNoise.
 * Nombres SRG sacados de las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$EndIslandDensityFunction", remap = false)
public abstract class EndIslandCacheMixin {

    @Shadow(remap = false)
    @Final
    private SimplexNoise f_208627_;

    @Unique
    private final PureValueCaches.EndIslandHeights bteopt$heights = new PureValueCaches.EndIslandHeights();

    @Shadow(remap = false)
    private static float m_224062_(SimplexNoise noise, int x, int z) {
        throw new AssertionError();
    }

    /**
     * @author bte_optimization_fixes
     * @reason Misma cuenta que vanilla, con la altura guardada por (x / 8, z / 8).
     */
    @Overwrite(remap = false)
    public double m_207386_(DensityFunction.FunctionContext context) {
        int x = context.blockX() / 8;
        int z = context.blockZ() / 8;
        float height;
        if (!WorldgenOpt.enabled) {
            height = m_224062_(this.f_208627_, x, z);
        } else {
            height = this.bteopt$heights.get(x, z);
            if (Float.isNaN(height)) {
                height = m_224062_(this.f_208627_, x, z);
                this.bteopt$heights.put(x, z, height);
            } else if (WorldgenOpt.verify) {
                float slow = m_224062_(this.f_208627_, x, z);
                WorldgenOpt.ISLANDS_VERIFIED.increment();
                if (Float.floatToRawIntBits(slow) != Float.floatToRawIntBits(height)) {
                    WorldgenOpt.ISLANDS_MISMATCH.increment();
                    WorldgenOpt.mismatch("altura de isla distinta en " + x + ", " + z + ": caché " + height + ", vanilla " + slow);
                }
                height = slow;
            }
        }
        return ((double) height - 8.0) / 128.0;
    }
}
