package com.endersjourney.bteoptimizationfixes.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;
import java.util.Deque;

/**
 * Pide por adelantado (con un ticket propio, sin bloquear el hilo del servidor) los chunks del End que BlockEnd
 * limpia al construir su isla, y envuelve cada tarea de escaneo para que espere a su chunk sin parar el tick.
 * Ver BlockEndCleanAreaMixin. Va en una clase normal porque las clases anonimas dentro de un Mixin no son fiables.
 */
public final class BlockEndCleanTickets {

    private BlockEndCleanTickets() {
    }

    public static final TicketType<ChunkPos> CLEAN_AREA =
            TicketType.create("bteopt_blockend_clean", Comparator.comparingLong(ChunkPos::toLong));

    /** Tras este numero de intentos (2 por tick, ~60 s) se deja de esperar y se carga el chunk de forma sincrona. */
    private static final int MAX_WAIT_POLLS = 2400;

    /** Empieza a generar el chunk en los workers. Llamar desde el hilo del servidor. */
    public static void request(ServerLevel level, int cx, int cz) {
        ChunkPos pos = new ChunkPos(cx, cz);
        level.getChunkSource().addRegionTicket(CLEAN_AREA, pos, 0, pos);
    }

    /**
     * Tarea que ejecuta {@code scan} cuando el chunk ya esta cargado. Si aun no lo esta, se vuelve a poner al
     * PRINCIPIO de la cola (el orden de la cola no cambia) y lo intenta en la siguiente pasada.
     */
    public static Runnable whenLoaded(Deque<Runnable> tasks, ServerLevel level, int cx, int cz, Runnable scan) {
        return new Runnable() {
            private int polls;

            @Override
            public void run() {
                if (level.getChunkSource().getChunkNow(cx, cz) == null && ++polls < MAX_WAIT_POLLS) {
                    tasks.addFirst(this);
                    return;
                }
                scan.run(); // si aun no estaba (tope de espera), scan hace getChunk() sincrono como antes
                ChunkPos pos = new ChunkPos(cx, cz);
                level.getChunkSource().removeRegionTicket(CLEAN_AREA, pos, 0, pos);
            }
        };
    }
}
