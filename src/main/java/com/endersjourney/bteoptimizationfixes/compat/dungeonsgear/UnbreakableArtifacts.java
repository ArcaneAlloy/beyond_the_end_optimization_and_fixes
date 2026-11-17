package com.endersjourney.bteoptimizationfixes.compat.dungeonsgear;

import com.endersjourney.bteoptimizationfixes.mixin.item.ItemMaxDamageAccessor;
import net.minecraft.world.item.Item;

/**
 * Artefactos de Dungeons Gear (cuerno de cabra, totem de regeneracion, etc.) irrompibles. En Dungeons Libraries
 * 1.19.2-3.0.12 todos nacen con 64 usos (ArtifactItem: durability(64)) y al recargar los datapacks se les vuelve a
 * poner la durabilidad de su config (ArtifactGearConfig.getDurability, tambien 64). Con 0 usos maximos el objeto deja
 * de gastarse: hurtAndBreak no hace nada, no hay barra de durabilidad y no se rompe.
 * <ul>
 *   <li>ArtifactUnbreakableMixin: pone la durabilidad maxima a 0 al crear cada artefacto y cada vez que se recarga su
 *   config.</li>
 *   <li>ItemStackDamageTagMixin: a los artefactos ya gastados de mundos antiguos se les quita el desgaste ("Damage") al
 *   cargarlos, estén donde estén (inventario, ranuras de Curios, cofres...). Los que ya se rompieron no vuelven.</li>
 * </ul>
 */
public final class UnbreakableArtifacts {

    private static final String ARTIFACT_CLASS = "com.infamous.dungeons_libraries.items.artifacts.ArtifactItem";
    private static Class<?> artifactClass;
    private static boolean looked;

    private UnbreakableArtifacts() {}

    /** Llamado desde ArtifactUnbreakableMixin con el propio artefacto. */
    public static void apply(Object artifact) {
        ((ItemMaxDamageAccessor) artifact).setBteoptMaxDamage(0);
    }

    /** Si el objeto es un artefacto de Dungeons Libraries (false si el mod no esta). */
    public static boolean isArtifact(Item item) {
        if (item == null) return false;
        if (!looked) {
            try {
                artifactClass = Class.forName(ARTIFACT_CLASS, false, UnbreakableArtifacts.class.getClassLoader());
            } catch (Throwable t) {
                artifactClass = null;
            }
            looked = true;
        }
        return artifactClass != null && artifactClass.isInstance(item);
    }
}
