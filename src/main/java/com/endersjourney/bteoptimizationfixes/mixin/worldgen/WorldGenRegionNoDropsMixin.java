package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import net.minecraft.server.level.WorldGenRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Bloqueo de la generación del mundo (el juego se congela para siempre; en la semilla 777 al 70,92 % del Overworld).
 *
 * Volcado de hilos del vigilante de ModernFix: un hilo de generación coloca el cercado de un outpost de Savage &
 * Ravage (EnclosureFeature.generateDecorations), actualiza los bloques vecinos y una planta de dos bloques que ya no
 * se sostiene se rompe con WorldGenRegion.destroyBlock(pos, dropBlock = true, ...). Al calcular lo que suelta, su
 * tabla de botín (hierba alta, helecho grande, tall_beachgrass de Upgrade Aquatic...) comprueba con location_check el
 * bloque de arriba o de abajo, y esa comprobación no mira la zona en generación sino el mundo real: pide al hilo
 * principal un chunk que aún no existe. El hilo principal está esperando justo a que ese hilo termine su chunk, y los
 * dos se quedan esperándose.
 *
 * Arreglo: durante la generación, romper un bloque no suelta objetos (dropBlock siempre false). El bloque se rompe
 * igual; solo se deja de calcular el botín, que además aparecería como objetos tirados en un chunk recién creado.
 * El terreno y las estructuras salen exactamente iguales.
 *
 * m_7740_ = WorldGenRegion.destroyBlock(BlockPos, boolean, Entity, int). Nombre SRG sacado del mismo volcado de hilos
 * (Block.updateOrDestroy -> WorldGenRegion.m_7740_ en WorldGenRegion.java:208). Es el único boolean de sus parámetros.
 */
@Mixin(value = WorldGenRegion.class, remap = false)
public abstract class WorldGenRegionNoDropsMixin {

    @ModifyVariable(method = "m_7740_", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private boolean bteopt$neverDropDuringWorldgen(boolean dropBlock) {
        return false;
    }
}
