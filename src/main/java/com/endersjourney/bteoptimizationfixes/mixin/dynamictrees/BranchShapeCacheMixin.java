package com.endersjourney.bteoptimizationfixes.mixin.dynamictrees;

import com.ferreusveritas.dynamictrees.block.branch.BasicBranchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic Trees 1.2.2: BasicBranchBlock.getShape (m_5940_) construye la forma de la rama EN CADA LLAMADA: un cubo
 * central del grosor de la rama y, por cada lado conectado, una caja unida con Shapes.or (que fusiona las formas
 * voxel a voxel). Se llama para cada choque de una entidad con una rama (mobs, pájaros de Alex's Mobs posados o
 * volando entre árboles...) y era ~7% del tick del servidor en la prueba de partida, más otro ~4% en Lithium
 * fusionando esas formas.
 *
 * La forma depende solo del radio de la rama y del radio de conexión de cada uno de sus 6 lados (como mucho el de la
 * rama), así que se guarda por esa combinación: al principio se calculan esos 7 números igual que el original (mismos
 * métodos, también los que redefinen las subclases); si esa forma ya se construyó, se devuelve; si no, se deja que el
 * método original la construya y se guarda. Las VoxelShape no se modifican nunca, así que compartirlas es seguro
 * (vanilla también las comparte). La forma devuelta es exactamente la misma.
 */
@Mixin(value = BasicBranchBlock.class, remap = false)
public abstract class BranchShapeCacheMixin {

    @Shadow
    public abstract int getRadius(BlockState state);

    @Shadow
    protected abstract int getSideConnectionRadius(BlockGetter level, BlockPos pos, int radius, Direction side);

    @Unique
    private static final Map<Long, VoxelShape> ejfixes$SHAPES = new ConcurrentHashMap<>();
    @Unique
    private static final Direction[] ejfixes$DIRS = Direction.values();
    /** Clave de la llamada en curso (en cada hilo), para guardar la forma al salir; -1 si no se guarda. */
    @Unique
    private static final ThreadLocal<long[]> ejfixes$PENDING = ThreadLocal.withInitial(() -> new long[]{-1});

    @Inject(method = "m_5940_", at = @At("HEAD"), cancellable = true)
    private void ejfixes$cachedShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                     CallbackInfoReturnable<VoxelShape> cir) {
        long[] pending = ejfixes$PENDING.get();
        pending[0] = -1;
        int radius = getRadius(state);
        if (radius < 0 || radius > 255) return;
        long key = radius;
        for (Direction dir : ejfixes$DIRS) {
            int c = Math.min(getSideConnectionRadius(level, pos, radius, dir), radius);
            // El original solo añade el brazo si es > 0: todos los valores <= 0 dan la misma forma
            if (c < 0) c = 0;
            key = (key << 8) | c;
        }
        VoxelShape shape = ejfixes$SHAPES.get(key);
        if (shape != null) {
            cir.setReturnValue(shape);
        } else {
            pending[0] = key;
        }
    }

    @Inject(method = "m_5940_", at = @At("RETURN"))
    private void ejfixes$storeShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                    CallbackInfoReturnable<VoxelShape> cir) {
        long[] pending = ejfixes$PENDING.get();
        if (pending[0] >= 0 && cir.getReturnValue() != null) ejfixes$SHAPES.putIfAbsent(pending[0], cir.getReturnValue());
        pending[0] = -1;
    }
}
