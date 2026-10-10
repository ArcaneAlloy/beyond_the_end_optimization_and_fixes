package com.endersjourney.bteoptimizationfixes.loading;

/**
 * Fase actual de la preparacion del mundo, para mostrarla en la pantalla de
 * carga cuando la barra ya esta al 100%.
 *
 * Al 100% los chunks de spawn ya estan listos, pero el servidor integrado
 * sigue trabajando: precarga de estructuras del lobby (Beyond The End) y,
 * en el primer tick, la creacion de los reinos de Beyond The End, BlockEnd
 * y Ender's Journey. Sin ninguna indicacion, el jugador ve un 100% quieto y
 * piensa que el juego se ha colgado.
 *
 * La escribe el hilo del servidor (WorldLoadingEvents y los Mixins de cada
 * mod) y la lee el hilo de render (WorldLoadingScreenOverlay). En un solo
 * jugador ambos viven en el mismo proceso, asi que basta con campos
 * volatile; en un servidor dedicado esta clase existe pero nadie la lee.
 */
public final class WorldLoadingStatus {

    public enum Phase {
        PREPARING("preparing"),
        LOBBY_STRUCTURES("lobby_structures"),
        BEYONDTHEEND_REALM("beyondtheend_realm"),
        BLOCKEND_REALM("blockend_realm"),
        ENDERS_JOURNEY_REALM("enders_journey_realm"),
        RELOADING_RECIPES("reloading_recipes"),
        FINISHING("finishing"),
        CONNECTING("connecting");

        private final String translationKey;

        Phase(String id) {
            this.translationKey = "bte_optimization_fixes.loading." + id;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    private static volatile Phase phase;
    private static volatile long startedAtMillis;

    private WorldLoadingStatus() {
    }

    /** Llamado al empezar ServerStartingEvent, justo cuando la barra llega al 100%. */
    public static void begin() {
        startedAtMillis = System.currentTimeMillis();
        phase = Phase.PREPARING;
    }

    public static void set(Phase newPhase) {
        if (phase != null) {
            phase = newPhase;
        }
    }

    public static void clear() {
        phase = null;
    }

    /** null mientras no haya una carga de mundo en su tramo final. */
    public static Phase getPhase() {
        return phase;
    }

    public static long getStartedAtMillis() {
        return startedAtMillis;
    }
}
