package com.endersjourney.bteoptimizationfixes.mixin.salmons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Vacío a propósito: sirve para que BteMixinPlugin.preApply reciba ClientEvents de Salmon's Genesis Reincarnation y
 * la deje sin código de cliente en el servidor dedicado (ver SalmonsServerSafety). Sin el mod no se aplica (@Pseudo).
 */
@Pseudo
@Mixin(targets = "com.mod.salmonsgenesisreincarnation.server.events.ClientEvents", remap = false)
public abstract class SalmonsClientEventsServerSafeMixin {
}
