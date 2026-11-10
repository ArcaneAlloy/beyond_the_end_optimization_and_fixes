package com.endersjourney.bteoptimizationfixes.compat.supplementaries;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Supplementaries 2.4.20, Pluma cartografica (CartographersQuillItem): la constante del nombre del mapa (TAG_NAME) vale
 * "decoration", igual que la de la marca (TAG_DECORATION). Al crear el mapa lee el nombre de la etiqueta "decoration" y
 * le pone al mapa translatable("minecraft:red_x"): sale el id de la marca como nombre.
 *
 * Arreglo: el nombre se lee de su propia etiqueta, "name". Puede ser una clave de idioma ("structure.bte_mobs.labyrinth")
 * o un texto JSON ({"translate":"item.bte_mobs.quill_map.name","with":[...]}). Sin "name", el mapa se queda con el nombre
 * normal de Minecraft.
 */
public final class QuillMapName {

    public static final String TAG_NAME = "name";

    private QuillMapName() {
    }

    /** Pone al mapa el nombre de la etiqueta "name" de la pluma. */
    public static void apply(ItemStack quill, ItemStack map) {
        if (map == null || map.isEmpty()) return;
        CompoundTag tag = quill.getTag();
        if (tag == null || !tag.contains(TAG_NAME, Tag.TAG_STRING)) return;
        String raw = tag.getString(TAG_NAME).trim();
        if (raw.isEmpty()) return;
        Component name = null;
        if (raw.startsWith("{") || raw.startsWith("[") || raw.startsWith("\"")) {
            try {
                name = Component.Serializer.fromJson(raw);
            } catch (RuntimeException ignored) {
                // no era JSON valido: se usa como clave de idioma
            }
        }
        if (name == null) name = Component.translatable(raw);
        map.setHoverName(name);
    }
}
