package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.debug.GenTiming;
import com.endersjourney.bteoptimizationfixes.debug.WorldgenProfiler;
import net.minecraft.util.thread.ProcessorMailbox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Herramienta de medición (ver GenTiming): cuánto tiempo está ocupada la cola "worldgen" de cada dimensión.
 * Por esa cola pasan, de una en una, las fases de generación que no se mandan a otra tarea (structure_starts, surface,
 * carvers, features...). Si está ocupada casi todo el tiempo, es ella la que marca el ritmo de Chunky.
 * m_18759_ = ProcessorMailbox.pollTask(): saca una tarea de la cola y la ejecuta. f_18738_ = ProcessorMailbox.name.
 * Nombres SRG sacados de las tablas de mapeo de Forge 1.19.2.
 */
@Mixin(value = ProcessorMailbox.class, remap = false)
public abstract class ProcessorMailboxTimingMixin {

    @Shadow(remap = false)
    @Final
    private String f_18738_;

    @Inject(method = "m_18759_", at = @At("HEAD"), remap = false)
    private void bteopt$start(CallbackInfoReturnable<Boolean> cir) {
        if ((GenTiming.enabled || WorldgenProfiler.running) && "worldgen".equals(this.f_18738_)) {
            if (GenTiming.enabled) GenTiming.mailboxStart();
            WorldgenProfiler.mailboxEnter();
        }
    }

    @Inject(method = "m_18759_", at = @At("RETURN"), remap = false)
    private void bteopt$end(CallbackInfoReturnable<Boolean> cir) {
        GenTiming.mailboxEnd(cir.getReturnValueZ());
        if (WorldgenProfiler.running && "worldgen".equals(this.f_18738_)) WorldgenProfiler.mailboxExit();
    }
}
