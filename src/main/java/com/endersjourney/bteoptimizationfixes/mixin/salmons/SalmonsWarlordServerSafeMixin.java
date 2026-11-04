package com.endersjourney.bteoptimizationfixes.mixin.salmons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Vacío a propósito: sirve para que BteMixinPlugin.preApply reciba NetherWarlord de Salmon's Genesis Reincarnation y
 * le quite en el servidor dedicado el cast a ClientEvents que lo tumba al morir el jefe (ver SalmonsServerSafety).
 * Sin el mod no se aplica (@Pseudo).
 */
@Pseudo
@Mixin(targets = "com.mod.salmonsgenesisreincarnation.server.entities.NetherWarlord", remap = false)
public abstract class SalmonsWarlordServerSafeMixin {
}
