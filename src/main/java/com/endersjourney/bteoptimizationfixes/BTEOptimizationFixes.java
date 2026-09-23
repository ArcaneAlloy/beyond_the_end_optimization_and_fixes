package com.endersjourney.bteoptimizationfixes;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Beyond The End - Optimization and Fixes
 *
 * Cajon general de fixes de rendimiento y compatibilidad, via Mixin, para
 * mods de TERCEROS usados en el modpack. No es el sitio para fixes propios
 * de Dungeons Gear (eso sigue en DungeonsGearAddon) — este addon es
 * especificamente para hallazgos sueltos de la investigacion de tiempo de
 * creacion de mundo: blockend, EuphoriaPatcher, y lo que vaya saliendo.
 *
 * Todos los Mixins de este addon llevan la anotacion @Pseudo (junto con
 * remap = false, ya que apuntan a clases de mods de terceros, no a
 * Minecraft/Forge). @Pseudo evita que el annotation processor exija tener
 * el jar del mod objetivo en el classpath de compilacion, y hace que, si
 * esa clase no existe en tiempo de ejecucion, el Mixin simplemente no se
 * aplique -- sin tirar abajo el resto de fixes de este addon.
 * Tambien incluye StructureUpgradeCommand: un comando de operador
 * ("/bteopt upgradestructures <entrada> <salida>") para convertir en
 * bloque estructuras NBT antiguas a la version de datos actual usando el
 * DataFixerUpper real del servidor -- pensado para preparar de antemano
 * los ficheros de mods/datapacks con NBTs desactualizados (ModernFix
 * avisa de varios: betterdungeons, idas, dungeons_arise, etc.) y evitar
 * ese coste en el primer arranque de cualquier jugador.
 */
@Mod(BTEOptimizationFixes.MODID)
public class BTEOptimizationFixes {
    public static final String MODID = "bte_optimization_fixes";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public BTEOptimizationFixes() {
        LOGGER.info("[{}] Cargado. Mixins activos se listan en {}.mixins.json", MODID, MODID);
    }
}
