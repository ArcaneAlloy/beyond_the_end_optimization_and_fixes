package com.endersjourney.bteoptimizationfixes.compat.mimic;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cofres "importantes": nunca se convierten en mimic (el mimic se queda con el cofre y su botin real se pierde).
 *
 * Un cofre es importante si:
 * - Su tabla de botin puede dar un objeto importante (MimicConfig: Ender Eyes de End Remastered, objetos de mision de
 *   KubeJS, la pluma del cartografo...). Se leen las tablas de los datapacks activos (la version que gana), siguiendo
 *   las referencias a otras tablas y las etiquetas de objetos.
 * - Un modificador global de botin (loot_modifiers) mete un objeto o una tabla importante en esa tabla (asi es como
 *   End Remastered pone sus ojos en los cofres vanilla).
 * - Esta en MimicConfig.important_loot_tables (las que tocan los scripts de LootJS).
 * - No tiene tabla pero ya lleva dentro un objeto importante.
 *
 * El analisis se hace la primera vez que hace falta y se repite al arrancar otro mundo o con /reload.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ImportantChests {

    private static volatile Set<ResourceLocation> importantTables;

    private ImportantChests() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        importantTables = null;
        MimicConfig.invalidate();
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        // Con /reload se envia a todos los jugadores (getPlayer() == null)
        if (event.getPlayer() == null) {
            importantTables = null;
            MimicConfig.invalidate();
        }
    }

    /** true si ese contenedor no debe convertirse en mimic. */
    public static boolean isImportant(MinecraftServer server, BlockEntity be) {
        if (server == null || be == null) return false;
        MimicConfig config = MimicConfig.get();
        CompoundTag nbt = be.saveWithoutMetadata();
        if (nbt.contains("LootTable", Tag.TAG_STRING)) {
            // Con tabla, el contenido aun no existe (y leerlo la desempaquetaria): solo cuenta la tabla
            ResourceLocation table = ResourceLocation.tryParse(nbt.getString("LootTable"));
            return table != null && tables(server).contains(table);
        }
        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && config.isImportantItem(ForgeRegistries.ITEMS.getKey(stack.getItem()))) return true;
            }
        }
        return false;
    }

    private static Set<ResourceLocation> tables(MinecraftServer server) {
        Set<ResourceLocation> t = importantTables;
        if (t != null) return t;
        synchronized (ImportantChests.class) {
            if (importantTables == null) importantTables = compute(server);
            return importantTables;
        }
    }

    private static Set<ResourceLocation> compute(MinecraftServer server) {
        long start = System.nanoTime();
        MimicConfig config = MimicConfig.get();
        ResourceManager rm = server.getResourceManager();
        Map<ResourceLocation, JsonElement> tables = readAll(rm, "loot_tables");
        Map<ResourceLocation, JsonElement> modifiers = readAll(rm, "loot_modifiers");

        // Por cada tabla: objetos, etiquetas y tablas a las que hace referencia
        Map<ResourceLocation, Set<ResourceLocation>> refs = new HashMap<>();
        Set<ResourceLocation> important = new HashSet<>(config.importantLootTables);
        Map<TagKey<Item>, Boolean> tagCache = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : tables.entrySet()) {
            Scan scan = new Scan();
            scan.walk(e.getValue());
            refs.put(e.getKey(), scan.tables);
            if (scan.hasImportant(config, tagCache)) important.add(e.getKey());
        }
        // Tablas que llaman a una importante tambien lo son
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<ResourceLocation, Set<ResourceLocation>> e : refs.entrySet()) {
                if (important.contains(e.getKey())) continue;
                for (ResourceLocation r : e.getValue()) {
                    if (important.contains(r)) {
                        important.add(e.getKey());
                        changed = true;
                        break;
                    }
                }
            }
        }
        // Modificadores globales: si meten algo importante, las tablas que nombran pasan a ser importantes
        int fromModifiers = 0;
        for (JsonElement json : modifiers.values()) {
            List<String> strings = new ArrayList<>();
            collectStrings(json, strings);
            boolean addsImportant = false;
            for (String s : strings) {
                ResourceLocation rl = ResourceLocation.tryParse(s);
                if (rl == null) continue;
                if (config.isImportantItem(rl) && ForgeRegistries.ITEMS.containsKey(rl) || important.contains(rl)) {
                    addsImportant = true;
                    break;
                }
            }
            if (!addsImportant) continue;
            for (String s : strings) {
                ResourceLocation rl = ResourceLocation.tryParse(s);
                if (rl != null && tables.containsKey(rl) && important.add(rl)) fromModifiers++;
            }
        }
        BTEOptimizationFixes.LOGGER.info("[bteopt] Cofres importantes (nunca seran mimic): {} tablas de botin de {} ({} por modificadores) en {} ms. Lista en logs/bteopt_important_loot_tables.txt",
                important.size(), tables.size(), fromModifiers, (System.nanoTime() - start) / 1_000_000);
        try {
            List<String> lines = new ArrayList<>();
            important.stream().map(ResourceLocation::toString).sorted().forEach(lines::add);
            java.nio.file.Files.write(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("logs/bteopt_important_loot_tables.txt"), lines);
        } catch (Exception ignored) {
            // solo es informativo
        }
        return Collections.unmodifiableSet(important);
    }

    private static Map<ResourceLocation, JsonElement> readAll(ResourceManager rm, String dir) {
        Map<ResourceLocation, JsonElement> out = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : rm.listResources(dir, p -> p.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = e.getKey();
            String path = file.getPath().substring(dir.length() + 1, file.getPath().length() - ".json".length());
            try (Reader r = e.getValue().openAsReader()) {
                out.put(new ResourceLocation(file.getNamespace(), path), JsonParser.parseReader(r));
            } catch (Exception ignored) {
                // JSON roto: el juego ya lo avisa al cargar
            }
        }
        return out;
    }

    private static void collectStrings(JsonElement json, List<String> out) {
        if (json == null) return;
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            out.add(json.getAsString());
        } else if (json.isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray()) collectStrings(e, out);
        } else if (json.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) collectStrings(e.getValue(), out);
        }
    }

    /** Recorre una tabla de botin: entradas de objeto, de etiqueta y de otra tabla, a cualquier profundidad. */
    private static final class Scan {
        final Set<ResourceLocation> items = new HashSet<>();
        final Set<ResourceLocation> tags = new HashSet<>();
        final Set<ResourceLocation> tables = new HashSet<>();

        void walk(JsonElement json) {
            if (json == null) return;
            if (json.isJsonArray()) {
                for (JsonElement e : json.getAsJsonArray()) walk(e);
                return;
            }
            if (!json.isJsonObject()) return;
            JsonObject o = json.getAsJsonObject();
            if (o.has("type") && o.has("name") && o.get("type").isJsonPrimitive() && o.get("name").isJsonPrimitive()) {
                String type = o.get("type").getAsString();
                ResourceLocation name = ResourceLocation.tryParse(o.get("name").getAsString());
                if (name != null) {
                    if (type.endsWith("item")) items.add(name);
                    else if (type.endsWith("tag")) tags.add(name);
                    else if (type.endsWith("loot_table")) tables.add(name);
                }
            }
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (e.getValue().isJsonObject() || e.getValue().isJsonArray()) walk(e.getValue());
            }
        }

        boolean hasImportant(MimicConfig config, Map<TagKey<Item>, Boolean> tagCache) {
            for (ResourceLocation item : items) if (config.isImportantItem(item)) return true;
            for (ResourceLocation tag : tags) {
                TagKey<Item> key = TagKey.create(Registry.ITEM_REGISTRY, tag);
                Boolean cached = tagCache.get(key);
                if (cached == null) {
                    cached = false;
                    for (Holder<Item> h : Registry.ITEM.getTagOrEmpty(key)) {
                        if (config.isImportantItem(ForgeRegistries.ITEMS.getKey(h.value()))) {
                            cached = true;
                            break;
                        }
                    }
                    tagCache.put(key, cached);
                }
                if (cached) return true;
            }
            return false;
        }
    }
}
