package com.endersjourney.bteoptimizationfixes.compat.quark;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Quark 3.4, cuevas de cristales (Corundum): el generador decora las cuevas que ya existen (suelo, techo, paredes) dentro
 * de una esfera grande, y para el cuenta como "cueva" cualquier hueco de aire, tambien el interior de una estructura.
 * Por eso el Jungle Monument de -4274 70 -3640 salio lleno de corundum.
 *
 * Aqui se calculan las cajas de las piezas de todas las estructuras que tocan el chunk que se esta decorando (con
 * PIECE_MARGIN bloques de margen, para que los cristales que crecen desde la pared tampoco entren) y el generador no
 * pone nada dentro. El resto de la cueva se decora igual que antes.
 */
public final class CorundumStructureGuard {

    /** Los cristales crecen varios bloques desde la pared: margen alrededor de cada pieza de estructura. */
    private static final int PIECE_MARGIN = 4;

    private CorundumStructureGuard() {
    }

    /** Cajas (ya con margen) de las piezas de estructura que tocan ese chunk. */
    public static List<BoundingBox> boxesFor(WorldGenRegion region, int chunkX, int chunkZ) {
        List<BoundingBox> out = new ArrayList<>();
        ChunkAccess chunk = region.getChunk(chunkX, chunkZ, ChunkStatus.STRUCTURE_REFERENCES, false);
        if (chunk == null) return out;
        int minX = chunkX << 4, minZ = chunkZ << 4;
        BoundingBox column = new BoundingBox(minX, region.getMinBuildHeight(), minZ, minX + 15, region.getMaxBuildHeight(), minZ + 15);
        for (Map.Entry<Structure, LongSet> entry : chunk.getAllReferences().entrySet()) {
            for (long startPos : entry.getValue()) {
                ChunkAccess startChunk = region.getChunk(ChunkPos.getX(startPos), ChunkPos.getZ(startPos),
                        ChunkStatus.STRUCTURE_STARTS, false);
                if (startChunk == null) continue;
                StructureStart start = startChunk.getStartForStructure(entry.getKey());
                if (start == null || !start.isValid()) continue;
                for (StructurePiece piece : start.getPieces()) {
                    BoundingBox box = piece.getBoundingBox().inflatedBy(PIECE_MARGIN);
                    if (box.intersects(column)) out.add(box);
                }
            }
        }
        return out;
    }

    public static boolean inside(List<BoundingBox> boxes, BlockPos pos) {
        for (BoundingBox box : boxes) {
            if (box.isInside(pos)) return true;
        }
        return false;
    }
}
