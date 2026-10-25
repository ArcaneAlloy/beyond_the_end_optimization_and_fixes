package com.endersjourney.bteoptimizationfixes.worldgen;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;

import java.util.ArrayList;
import java.util.List;

/**
 * Análisis de un BlockPredicate para el escaneo rápido (ver ColumnScan): averigua si el resultado depende solo de los
 * bloques en unas posiciones fijas relativas a la posición probada, y cuáles son esas posiciones.
 *
 * Es así para: comprobaciones de estado (StateTestingPredicate: matching_blocks, matching_block_tag, matching_fluids,
 * solid, replaceable... todas miran solo el bloque en posición + offset), "true", "not" y "all_of"/"any_of" de
 * predicados que también lo cumplan. Cualquier otro tipo (would_survive, has_sturdy_face, inside_world_bounds, o tipos
 * de otros mods) hace que se use el camino de vanilla.
 *
 * Las clases de predicado de vanilla no son públicas; las interfaces de abajo las implementan mixins en
 * mixin/worldgen (BlockPredicateShapeMixins).
 */
public final class BlockPredicateShape {

    public interface StateLeaf {
        Vec3i bteopt$offset();
    }

    public interface Combining {
        List<BlockPredicate> bteopt$children();
    }

    public interface Negated {
        BlockPredicate bteopt$inner();
    }

    private BlockPredicateShape() {
    }

    /**
     * Offsets (x, y, z seguidos) de todas las comprobaciones de estado de los predicados dados, o null si alguno no se
     * puede analizar.
     */
    public static int[] leafOffsets(BlockPredicate... predicates) {
        List<Vec3i> out = new ArrayList<>();
        for (BlockPredicate p : predicates) {
            if (!collect(p, out, 0)) return null;
        }
        int[] r = new int[out.size() * 3];
        for (int i = 0; i < out.size(); i++) {
            r[i * 3] = out.get(i).getX();
            r[i * 3 + 1] = out.get(i).getY();
            r[i * 3 + 2] = out.get(i).getZ();
        }
        return r;
    }

    private static boolean collect(BlockPredicate p, List<Vec3i> out, int depth) {
        if (depth > 32 || p == null) return false;
        if (p == BlockPredicate.alwaysTrue()) return true;
        if (p instanceof StateLeaf leaf) {
            out.add(leaf.bteopt$offset());
            return true;
        }
        if (p instanceof Negated not) return collect(not.bteopt$inner(), out, depth + 1);
        if (p instanceof Combining comb) {
            for (BlockPredicate c : comb.bteopt$children()) {
                if (!collect(c, out, depth + 1)) return false;
            }
            return true;
        }
        return false;
    }
}
