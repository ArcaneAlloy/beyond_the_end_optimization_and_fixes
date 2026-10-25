package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.worldgen.ColumnScan;
import com.endersjourney.bteoptimizationfixes.worldgen.WorldgenOpt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.CountOnEveryLayerPlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Optimización exacta (ver WorldgenOpt, punto 7): el modificador "count_on_every_layer" (vegetación del Nether: hongos,
 * raíces, brotes...) elige columnas al azar y, para cada capa, baja la columna entera desde arriba hasta encontrar el
 * suelo número N. Para la capa 3 vuelve a leer todo lo que ya leyó para las capas 0, 1 y 2. Aquí cada columna se baja
 * una sola vez por llamada y se apuntan todos sus suelos; las secciones de solo aire se saltan de golpe (todas sus
 * lecturas valen AIR). Mientras dura getPositions no se coloca nada (vanilla construye toda la lista antes de
 * devolverla), así que la columna no cambia entre una capa y otra. El sorteo de posiciones y su orden son los mismos.
 * m_213676_ = getPositions(...), m_191612_ = findOnGroundYPosition(...), m_191608_ = isEmpty(BlockState),
 * f_191600_ = count. Nombres SRG sacados de las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(value = CountOnEveryLayerPlacement.class, remap = false)
public abstract class CountOnEveryLayerFastMixin {

    @Shadow(remap = false)
    @Final
    private IntProvider f_191600_;

    @Shadow(remap = false)
    private static int m_191612_(PlacementContext context, int x, int y, int z, int layer) {
        throw new AssertionError();
    }

    @Shadow(remap = false)
    private static boolean m_191608_(BlockState state) {
        throw new AssertionError();
    }

    /**
     * @author bte_optimization_fixes
     * @reason Mismo resultado y mismo uso del aleatorio que vanilla, leyendo cada columna una sola vez.
     */
    @Overwrite(remap = false)
    public Stream<BlockPos> m_213676_(PlacementContext context, RandomSource random, BlockPos origin) {
        Stream.Builder<BlockPos> builder = Stream.builder();
        boolean useCache = WorldgenOpt.enabled && context.getLevel() instanceof WorldGenRegion;
        int[][] grounds = useCache ? new int[256][] : null;
        int[] startY = useCache ? new int[256] : null;
        int layer = 0;
        boolean found;
        do {
            found = false;
            for (int i = 0; i < this.f_191600_.sample(random); ++i) {
                int x = random.nextInt(16) + origin.getX();
                int z = random.nextInt(16) + origin.getZ();
                int y = context.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int groundY;
                if (useCache) {
                    int idx = (x - origin.getX()) * 16 + (z - origin.getZ());
                    int[] g = grounds[idx];
                    if (g == null || startY[idx] != y) {
                        g = bteopt$scanColumn(context, x, y, z);
                        grounds[idx] = g;
                        startY[idx] = y;
                    }
                    groundY = layer < g.length ? g[layer] : Integer.MAX_VALUE;
                    if (WorldgenOpt.verify) {
                        int slow = m_191612_(context, x, y, z, layer);
                        WorldgenOpt.SCANS_VERIFIED.increment();
                        if (slow != groundY) {
                            WorldgenOpt.SCANS_MISMATCH.increment();
                            WorldgenOpt.mismatch("count_on_every_layer distinto en " + x + ", " + z + " capa " + layer + ": rápido " + groundY + ", vanilla " + slow);
                        }
                        groundY = slow;
                    }
                } else {
                    groundY = m_191612_(context, x, y, z, layer);
                }
                if (groundY == Integer.MAX_VALUE) continue;
                builder.add(new BlockPos(x, groundY, z));
                found = true;
            }
            ++layer;
        } while (found);
        return builder.build();
    }

    /**
     * Todos los resultados de findOnGroundYPosition para esta columna, en orden de capa: la misma lectura de arriba
     * abajo, apuntando cada suelo en vez de parar en el N-ésimo.
     */
    @Unique
    private static int[] bteopt$scanColumn(PlacementContext context, int x, int startY, int z) {
        WorldGenRegion region = (WorldGenRegion) context.getLevel();
        int[] out = new int[8];
        int n = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, startY, z);
        BlockState prev = context.getBlockState(pos);
        int minY = context.getMinBuildHeight();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int yy = startY; yy >= minY + 1; --yy) {
            int ny = yy - 1;
            if (ColumnScan.airSection(region, x, ny, z) != null) {
                // Toda la sección vale AIR: ningún suelo hasta su borde inferior y el último bloque leído es AIR
                int bottom = Math.max(ny & ~15, minY);
                prev = air;
                yy = bottom + 1;
                continue;
            }
            pos.setY(ny);
            BlockState state = context.getBlockState(pos);
            if (!m_191608_(state) && m_191608_(prev) && !state.is(Blocks.BEDROCK)) {
                if (n == out.length) out = Arrays.copyOf(out, n * 2);
                out[n++] = ny + 1;
            }
            prev = state;
        }
        return Arrays.copyOf(out, n);
    }
}
