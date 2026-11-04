package com.endersjourney.bteoptimizationfixes.mixin.salmons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Vacío a propósito: sirve para que BteMixinPlugin.preApply reciba la clase principal de Salmon's Genesis
 * Reincarnation y la arregle en el servidor dedicado (ver SalmonsServerSafety). Sin el mod no se aplica (@Pseudo).
 */
@Pseudo
@Mixin(targets = "com.mod.salmonsgenesisreincarnation.salmonsgenesisreincarnation", remap = false)
public abstract class SalmonsMainServerSafeMixin {
}
