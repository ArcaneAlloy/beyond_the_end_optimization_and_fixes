package com.endersjourney.bteoptimizationfixes.mixin.vtweaks;

import net.minecraftforge.fml.util.thread.EffectiveSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * V-Tweaks 1.19.2-4.0.13: las particulas de los mobs Challenger (ChallengerParticleHandler.process, en cada
 * LivingTickEvent) llaman a VTweaks.Proxy.addParticle sin mirar el lado. En el cliente el proxy es ClientProxy, que las
 * mete directamente en el ParticleEngine de Minecraft. En un mundo de un jugador el servidor integrado tambien dispara
 * LivingTickEvent, asi que el hilo del servidor escribe en la cola de particulas (un ArrayDeque) a la vez que el hilo
 * de render. La cola se corrompe, crece sin limite y el juego acaba en OutOfMemoryError: Java heap space dentro de
 * ParticleEngine.add (crash "Exception while adding particle", con cualquier particula: splash de agua, etc.).
 *
 * Arreglo: ClientProxy.addParticle no hace nada cuando lo llama el hilo del servidor integrado. No se pierde nada: el
 * cliente procesa los mismos mobs en su propio LivingTickEvent y pinta las mismas particulas. En servidor dedicado no
 * cambia nada (alli el proxy es CommonProxy, que manda paquetes, y este mixin es solo de cliente).
 *
 * Verificado por desensamblado de VTweaks-1.19.2-4.0.13.jar: ClientProxy.addParticle(FFFDDD)V llama a
 * showDustParticle, que usa Minecraft.particleEngine.createParticle; ChallengerParticleHandler.process no comprueba
 * level.isClientSide.
 */
@Pseudo
@Mixin(targets = "com.oitsjustjose.vtweaks.client.ClientProxy", remap = false)
public abstract class ChallengerParticleRenderThreadMixin {

    @Inject(method = "addParticle", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$notFromIntegratedServer(float r, float g, float b, double x, double y, double z, CallbackInfo ci) {
        if (EffectiveSide.get().isServer()) ci.cancel();
    }
}
