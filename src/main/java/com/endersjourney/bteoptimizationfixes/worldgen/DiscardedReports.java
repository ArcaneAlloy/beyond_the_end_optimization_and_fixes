package com.endersjourney.bteoptimizationfixes.worldgen;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;

/**
 * Marca (por hilo) que estamos dentro de StructureTemplate#createEntityIgnoreException: si la entidad de la
 * estructura falla al cargarse, Minecraft construye un CrashReport completo (y su SystemReport consulta por OSHI la
 * tarjeta grafica, la CPU y la memoria, varios ms cada vez) para luego tirarlo. Ver SystemReportHardwareSkipMixin.
 */
public final class DiscardedReports {

    private DiscardedReports() {
    }

    private static final ThreadLocal<Boolean> INSIDE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static volatile boolean logged;

    public static void enter() {
        INSIDE.set(Boolean.TRUE);
    }

    public static void exit() {
        INSIDE.set(Boolean.FALSE);
    }

    public static boolean skipHardware() {
        if (!INSIDE.get()) return false;
        if (!logged) {
            logged = true;
            BTEOptimizationFixes.LOGGER.info("[{}] Entidad de estructura que no se pudo cargar: se omite la informacion de hardware del informe descartado (solo se avisa una vez)",
                    BTEOptimizationFixes.MODID);
        }
        return true;
    }
}
