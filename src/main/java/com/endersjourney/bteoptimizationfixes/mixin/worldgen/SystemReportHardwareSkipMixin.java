package com.endersjourney.bteoptimizationfixes.mixin.worldgen;

import com.endersjourney.bteoptimizationfixes.worldgen.DiscardedReports;
import net.minecraft.SystemReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * El constructor de SystemReport rellena la parte de hardware con ignoreErrors("hardware", ...) (m_143516_), que
 * consulta por OSHI la tarjeta grafica, la CPU y la memoria. Cuando el informe se crea dentro de
 * StructureTemplate#createEntityIgnoreException (y por tanto se va a descartar), esa parte se omite.
 * Cualquier otro CrashReport (crasheos reales) sale completo como siempre.
 */
@Mixin(value = SystemReport.class, remap = false)
public abstract class SystemReportHardwareSkipMixin {

    @Shadow(remap = false)
    private void m_143516_(String name, Runnable action) {
    }

    @Redirect(method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/SystemReport;m_143516_(Ljava/lang/String;Ljava/lang/Runnable;)V", remap = false),
            remap = false, require = 0)
    private void bteopt$skipHardware(SystemReport report, String name, Runnable action) {
        if (DiscardedReports.skipHardware()) return;
        this.m_143516_(name, action);
    }
}
