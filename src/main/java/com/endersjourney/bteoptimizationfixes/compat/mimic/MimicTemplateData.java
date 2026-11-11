package com.endersjourney.bteoptimizationfixes.compat.mimic;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Datos del mundo (por dimension) para los mimics de las estructuras de plantillas:
 * - Cuantos mimics lleva ya cada estructura (para el maximo por estructura). La estructura se genera chunk a chunk, en
 *   varios hilos y a veces en varias sesiones, asi que la cuenta se guarda con el mundo.
 * - Que marcadores son "forzados": el cofre ya se eligio al generar, asi que el marcador no vuelve a tirar la
 *   probabilidad del mod Mimic (ver MimicSpawnerImportantChestMixin).
 *
 * Se obtiene en el hilo del servidor al cargar la dimension; los hilos de generacion solo usan el objeto ya creado.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MimicTemplateData extends SavedData {

    private static final String NAME = "bte_optimization_fixes_mimics";
    private static final Map<ServerLevel, MimicTemplateData> LOADED = new ConcurrentHashMap<>();

    private final Map<String, Integer> counts = new HashMap<>();
    private final LongSet forced = new LongOpenHashSet();

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LOADED.put(level, level.getDataStorage().computeIfAbsent(MimicTemplateData::load, MimicTemplateData::new, NAME));
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LOADED.remove(level);
    }

    public static MimicTemplateData get(ServerLevel level) {
        return LOADED.get(level);
    }

    /** Reserva un mimic para esa estructura si aun no llego al maximo. */
    public synchronized boolean tryReserve(String structureKey, int max, long markerPos) {
        int n = counts.getOrDefault(structureKey, 0);
        if (n >= max) return false;
        counts.put(structureKey, n + 1);
        forced.add(markerPos);
        setDirty();
        return true;
    }

    /** true (y lo olvida) si el marcador de esa posicion es forzado. */
    public synchronized boolean consumeForced(long markerPos) {
        if (!forced.remove(markerPos)) return false;
        setDirty();
        return true;
    }

    private static MimicTemplateData load(CompoundTag tag) {
        MimicTemplateData data = new MimicTemplateData();
        CompoundTag counts = tag.getCompound("Counts");
        for (String key : counts.getAllKeys()) data.counts.put(key, counts.getInt(key));
        for (long pos : tag.getLongArray("Forced")) data.forced.add(pos);
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        CompoundTag c = new CompoundTag();
        counts.forEach(c::putInt);
        tag.put("Counts", c);
        tag.put("Forced", new LongArrayTag(forced.toLongArray()));
        return tag;
    }
}
