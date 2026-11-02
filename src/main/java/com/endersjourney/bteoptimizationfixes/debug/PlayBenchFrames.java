package com.endersjourney.bteoptimizationfixes.debug;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiempos de frame del cliente durante una fase de playbench. Lo llena PlayBenchClient (solo en el cliente, en el
 * mismo proceso que el servidor integrado) y lo lee PlayBench al cerrar cada fase. En un servidor dedicado se queda
 * vacío.
 */
public final class PlayBenchFrames {

    private PlayBenchFrames() {
    }

    /** true mientras una fase está midiendo (lo pone PlayBench). */
    public static volatile boolean recording;
    /** Ajustes de vídeo del cliente que influyen en los FPS (lo rellena PlayBenchClient). */
    public static volatile String clientInfo = "-";

    private static final List<Long> FRAMES = new ArrayList<>();

    /** Duración de un frame, en nanosegundos. */
    public static void frame(long nanos) {
        if (!recording) return;
        synchronized (FRAMES) {
            FRAMES.add(nanos);
        }
    }

    /** Devuelve los frames de la fase y vacía la lista. */
    public static long[] drain() {
        synchronized (FRAMES) {
            long[] out = FRAMES.stream().mapToLong(Long::longValue).toArray();
            FRAMES.clear();
            return out;
        }
    }
}
