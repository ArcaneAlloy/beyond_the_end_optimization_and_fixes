package com.endersjourney.bteoptimizationfixes.compat.geckolib.client;

import net.minecraft.client.renderer.MultiBufferSource;

import javax.annotation.Nullable;
import java.util.ArrayDeque;

/**
 * Buffer de dibujo que la capa de armaduras (HumanoidArmorLayer) esta usando ahora mismo, para que GeoArmorRenderer
 * dibuje en el mismo sitio que el resto de la armadura. Ver HumanoidArmorLayerSourceMixin y GeoArmorShadowBufferMixin.
 * Solo se usa desde el hilo de render.
 */
public final class ArmorLayerBufferSource {

    private static final ArrayDeque<MultiBufferSource> STACK = new ArrayDeque<>();

    private ArmorLayerBufferSource() {
    }

    public static void push(MultiBufferSource bufferSource) {
        if (bufferSource != null) STACK.push(bufferSource);
    }

    public static void pop(MultiBufferSource bufferSource) {
        if (bufferSource != null && !STACK.isEmpty()) STACK.pop();
    }

    /** El buffer de la capa de armaduras, o null si no se esta dibujando ninguna (se deja el de GeckoLib). */
    @Nullable
    public static MultiBufferSource current() {
        return STACK.peek();
    }
}
