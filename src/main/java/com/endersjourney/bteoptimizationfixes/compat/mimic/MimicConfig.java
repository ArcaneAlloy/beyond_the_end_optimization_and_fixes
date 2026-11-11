package com.endersjourney.bteoptimizationfixes.compat.mimic;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * config/bte_optimization_fixes-mimics.json. Se crea con los valores por defecto si no existe.
 *
 * - excluded_structures: estructuras donde los cofres de sus plantillas no se convierten en mimics.
 * - template_chest_chance: en las estructuras de plantillas, 1 de cada X cofres es mimic (10 = 1 de cada 10).
 * - max_mimics_per_structure: como mucho tantos mimics por estructura de plantillas.
 * - important_namespaces / important_items: objetos que hacen "importante" a un cofre (nunca sera mimic).
 * - important_loot_tables: tablas de botin importantes que no se pueden detectar leyendo los datapacks (las que
 *   modifican scripts de KubeJS/LootJS, p. ej. structure_loot_once.js).
 */
public final class MimicConfig {

    private static final String FILE = BTEOptimizationFixes.MODID + "-mimics.json";

    public final Set<ResourceLocation> excludedStructures;
    public final int templateChestChance;
    public final int maxMimicsPerStructure;
    public final Set<String> importantNamespaces;
    public final Set<ResourceLocation> importantItems;
    public final Set<ResourceLocation> importantLootTables;

    private static volatile MimicConfig instance;

    private MimicConfig(Set<ResourceLocation> excludedStructures, int templateChestChance, int maxMimicsPerStructure,
                        Set<String> importantNamespaces, Set<ResourceLocation> importantItems,
                        Set<ResourceLocation> importantLootTables) {
        this.excludedStructures = excludedStructures;
        this.templateChestChance = Math.max(1, templateChestChance);
        this.maxMimicsPerStructure = Math.max(0, maxMimicsPerStructure);
        this.importantNamespaces = importantNamespaces;
        this.importantItems = importantItems;
        this.importantLootTables = importantLootTables;
    }

    public static MimicConfig get() {
        MimicConfig c = instance;
        if (c == null) {
            synchronized (MimicConfig.class) {
                if (instance == null) instance = load();
                c = instance;
            }
        }
        return c;
    }

    /** Se vuelve a leer al arrancar un mundo o con /reload. */
    public static void invalidate() {
        instance = null;
    }

    private static JsonObject defaults() {
        JsonObject o = new JsonObject();
        o.add("excluded_structures", new JsonArray());
        o.addProperty("template_chest_chance", 10);
        o.addProperty("max_mimics_per_structure", 3);
        o.add("important_namespaces", array(List.of("endrem", "kubejs")));
        o.add("important_items", array(List.of("supplementaries:cartographers_quill")));
        o.add("important_loot_tables", array(List.of(
                // structure_loot_once.js: Nether Eye en los "quarters" de las fortalezas de YUNG
                "betterfortresses:chests/quarters",
                // structure_loot_once.js: pluma al Forbidden Castle en los cofres de los Bastiones
                "minecraft:chests/bastion_treasure",
                "minecraft:chests/bastion_other",
                "minecraft:chests/bastion_bridge",
                "minecraft:chests/bastion_hoglin_stable")));
        return o;
    }

    private static JsonArray array(List<String> values) {
        JsonArray a = new JsonArray();
        values.forEach(a::add);
        return a;
    }

    private static MimicConfig load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE);
        JsonObject json = defaults();
        try {
            if (Files.isRegularFile(path)) {
                try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonObject read = JsonParser.parseReader(r).getAsJsonObject();
                    for (String key : read.keySet()) json.add(key, read.get(key));
                }
            } else {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    gson.toJson(json, w);
                }
            }
        } catch (Exception e) {
            BTEOptimizationFixes.LOGGER.warn("[bteopt] No se pudo leer {}: {}", path, e.toString());
        }
        Set<String> namespaces = new LinkedHashSet<>();
        for (JsonElement e : json.getAsJsonArray("important_namespaces")) namespaces.add(e.getAsString());
        return new MimicConfig(ids(json, "excluded_structures"), json.get("template_chest_chance").getAsInt(),
                json.get("max_mimics_per_structure").getAsInt(), Collections.unmodifiableSet(namespaces),
                ids(json, "important_items"), ids(json, "important_loot_tables"));
    }

    private static Set<ResourceLocation> ids(JsonObject json, String key) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        if (json.has(key)) {
            for (JsonElement e : json.getAsJsonArray(key)) {
                ResourceLocation rl = ResourceLocation.tryParse(e.getAsString());
                if (rl != null) out.add(rl);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    public boolean isImportantItem(ResourceLocation id) {
        return id != null && (importantNamespaces.contains(id.getNamespace()) || importantItems.contains(id));
    }
}
