package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.worldgen.BlockPredicateShape;
import com.endersjourney.bteoptimizationfixes.worldgen.ColumnScan;
import com.endersjourney.bteoptimizationfixes.worldgen.WorldgenOpt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Optimización exacta (ver WorldgenOpt, punto 7): el modificador "environment_scan" (subir o bajar bloque a bloque
 * hasta encontrar dónde poner la feature) recorre muchísimo aire en el End y en las cuevas. Si los dos predicados solo
 * miran bloques en posiciones fijas (BlockPredicateShape) y todas esas posiciones están en secciones de solo aire,
 * los dos predicados dan siempre lo mismo hasta el borde de la sección, y si eso es "no es aquí, sigue", se salta de
 * golpe. En cualquier otro caso cada paso se hace exactamente como vanilla. Solo para escaneos verticales.
 * m_213676_ = getPositions(PlacementContext, RandomSource, BlockPos); f_191639_ = directionOfSearch,
 * f_191640_ = targetCondition, f_191641_ = allowedSearchCondition, f_191642_ = maxSteps.
 * Nombres SRG sacados de las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(value = EnvironmentScanPlacement.class, remap = false)
public abstract class EnvironmentScanFastMixin {

    @Shadow(remap = false)
    @Final
    private Direction f_191639_;

    @Shadow(remap = false)
    @Final
    private BlockPredicate f_191640_;

    @Shadow(remap = false)
    @Final
    private BlockPredicate f_191641_;

    @Shadow(remap = false)
    @Final
    private int f_191642_;

    /** Offsets de los predicados (x, y, z seguidos), o null si no se pueden analizar. Válido si bteopt$analyzed. */
    @Unique
    private int[] bteopt$offsets;

    @Unique
    private volatile boolean bteopt$analyzed;

    /**
     * @author bte_optimization_fixes
     * @reason Mismo resultado que vanilla, saltando tramos de secciones de solo aire.
     */
    @Overwrite(remap = false)
    public Stream<BlockPos> m_213676_(PlacementContext context, RandomSource random, BlockPos origin) {
        WorldGenLevel level = context.getLevel();
        if (!WorldgenOpt.enabled || this.f_191639_.getAxis() != Direction.Axis.Y || !(level instanceof WorldGenRegion region)) {
            return bteopt$wrap(bteopt$vanilla(level, origin));
        }
        int[] offsets;
        if (this.bteopt$analyzed) {
            offsets = this.bteopt$offsets;
        } else {
            offsets = BlockPredicateShape.leafOffsets(this.f_191640_, this.f_191641_);
            this.bteopt$offsets = offsets;
            this.bteopt$analyzed = true;
        }
        if (offsets == null) return bteopt$wrap(bteopt$vanilla(level, origin));
        BlockPos fast = bteopt$fast(region, origin, offsets);
        if (WorldgenOpt.verify) {
            BlockPos slow = bteopt$vanilla(level, origin);
            WorldgenOpt.SCANS_VERIFIED.increment();
            if (!Objects.equals(fast == null ? null : fast.immutable(), slow == null ? null : slow.immutable())) {
                WorldgenOpt.SCANS_MISMATCH.increment();
                WorldgenOpt.mismatch("environment_scan distinto desde " + origin + ": rápido " + fast + ", vanilla " + slow);
            }
            return bteopt$wrap(slow);
        }
        return bteopt$wrap(fast);
    }

    @Unique
    private static Stream<BlockPos> bteopt$wrap(BlockPos pos) {
        return pos == null ? Stream.of(new BlockPos[0]) : Stream.of(pos);
    }

    /** El bucle de vanilla, tal cual. Devuelve la posición encontrada o null. */
    @Unique
    private BlockPos bteopt$vanilla(WorldGenLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos pos = origin.mutable();
        if (!this.f_191641_.test(level, pos)) return null;
        for (int i = 0; i < this.f_191642_; ++i) {
            if (this.f_191640_.test(level, pos)) return pos;
            pos.move(this.f_191639_);
            if (level.isOutsideBuildHeight(pos.getY())) return null;
            if (!this.f_191641_.test(level, pos)) break;
        }
        return this.f_191640_.test(level, pos) ? pos : null;
    }

    /** El mismo bucle, saltando tramos en los que todas las posiciones que miran los predicados son de solo aire. */
    @Unique
    private BlockPos bteopt$fast(WorldGenRegion level, BlockPos origin, int[] offsets) {
        BlockPos.MutableBlockPos pos = origin.mutable();
        if (!this.f_191641_.test(level, pos)) return null;
        int dy = this.f_191639_.getStepY();
        int maxSteps = this.f_191642_;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int airTarget = -1, airAllowed = -1; // -1 = aún no se sabe; 0/1 = valor cuando todo es aire
        int i = 0;
        while (i < maxSteps) {
            int run = ColumnScan.airRun(level, pos.getX(), pos.getY(), pos.getZ(), offsets, dy);
            if (run > 0) {
                if (airTarget < 0) airTarget = this.f_191640_.test(level, pos) ? 1 : 0;
                if (airAllowed < 0) airAllowed = this.f_191641_.test(level, pos) ? 1 : 0;
                if (airTarget == 0 && airAllowed == 1) {
                    int n = Math.min(run, maxSteps - i);
                    int toOutside = dy > 0 ? maxY - pos.getY() : pos.getY() - minY + 1;
                    if (toOutside <= n) return null;
                    pos.setY(pos.getY() + n * dy);
                    i += n;
                    continue;
                }
            }
            if (this.f_191640_.test(level, pos)) return pos;
            pos.move(this.f_191639_);
            if (level.isOutsideBuildHeight(pos.getY())) return null;
            if (!this.f_191641_.test(level, pos)) break;
            ++i;
        }
        return this.f_191640_.test(level, pos) ? pos : null;
    }
}
