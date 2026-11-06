package com.endersjourney.bteoptimizationfixes.compat.mimic;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Mimic 1.2.38: el mimic tiene una caja de colision de exactamente 1 x 1 bloque. Con ese ancho:
 *
 * - El buscador de caminos lo trata como una criatura de 2 x 2 bloques (NodeEvaluator usa floor(ancho + 1)), asi que
 *   en pasillos de 1 bloque, puertas o huecos entre bloques no encuentra camino y se queda quieto o dando vueltas.
 * - Aunque encuentre camino, no le sobra ningun margen: en cuanto no va perfectamente centrado roza las esquinas y se
 *   queda atascado entre bloques.
 *
 * Se le pone un ancho de 0.9: el buscador de caminos lo trata como de 1 x 1 y le queda margen para no rozar. El alto no
 * cambia y el modelo se sigue viendo igual; solo es la caja de colision. Se aplica a todas las criaturas del mod Mimic.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MimicHitbox {

    private static final String MIMIC_NAMESPACE = "mimic";
    private static final float WIDTH = 0.9F;

    private MimicHitbox() {
    }

    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        EntityType<?> type = event.getEntity().getType();
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (id == null || !MIMIC_NAMESPACE.equals(id.getNamespace())) return;
        EntityDimensions size = event.getNewSize();
        if (size.width <= WIDTH) return;
        // Sin cambiar la altura de los ojos: solo se estrecha la caja
        event.setNewSize(size.fixed ? EntityDimensions.fixed(WIDTH, size.height) : EntityDimensions.scalable(WIDTH, size.height));
    }
}
