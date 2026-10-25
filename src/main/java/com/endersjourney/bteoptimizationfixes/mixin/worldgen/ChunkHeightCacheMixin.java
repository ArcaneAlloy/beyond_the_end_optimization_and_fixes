package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Optimización exacta (ver WorldgenOpt, punto 6): cada lectura de bloque de un chunk (ProtoChunk.getBlockState)
 * pregunta tres veces la altura mínima y una la altura del mundo, y cada pregunta pasa por el nivel, su tipo de
 * dimensión y el registro. Las features que bajan por columnas (vegetación del Nether, Nullscape...) leen millones de
 * bloques. Las dos alturas son fijas para un chunk (vienen de su LevelHeightAccessor, que no cambia), así que se
 * guardan en el propio chunk la primera vez.
 * m_141937_ = getMinBuildHeight(), m_141928_ = getHeight(), f_187611_ = levelHeightAccessor.
 * Nombres SRG sacados de las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(value = ChunkAccess.class, remap = false)
public abstract class ChunkHeightCacheMixin {

    @Shadow(remap = false)
    @Final
    protected LevelHeightAccessor f_187611_;

    @Unique
    private int bteopt$minY = Integer.MIN_VALUE;

    @Unique
    private int bteopt$height = -1;

    /**
     * @author bte_optimization_fixes
     * @reason El valor es fijo para el chunk; se guarda la primera vez.
     */
    @Overwrite(remap = false)
    public int m_141937_() {
        int v = this.bteopt$minY;
        if (v == Integer.MIN_VALUE) {
            v = this.f_187611_.getMinBuildHeight();
            this.bteopt$minY = v;
        }
        return v;
    }

    /**
     * @author bte_optimization_fixes
     * @reason El valor es fijo para el chunk; se guarda la primera vez.
     */
    @Overwrite(remap = false)
    public int m_141928_() {
        int v = this.bteopt$height;
        if (v < 0) {
            v = this.f_187611_.getHeight();
            this.bteopt$height = v;
        }
        return v;
    }
}
