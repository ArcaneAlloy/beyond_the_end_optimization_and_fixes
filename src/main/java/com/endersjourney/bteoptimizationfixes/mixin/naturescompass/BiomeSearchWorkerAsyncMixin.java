package com.endersjourney.bteoptimizationfixes.mixin.naturescompass;

import com.chaosthedude.naturescompass.items.NaturesCompassItem;
import com.endersjourney.bteoptimizationfixes.compat.naturescompass.AsyncBiomeSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nature's Compass 1.19.2-1.10.0: la busqueda de bioma (BiomeSearchWorker)
 * se hace en AsyncBiomeSearch, fuera del hilo del servidor. Aqui doWork()
 * solo arranca la busqueda, actualiza el radio que se ve en la brujula y, al
 * terminar, deja x/z/samples con el resultado y llama al succeed()/fail()
 * originales (mismo NBT, mismos mensajes, mismo log).
 *
 * Si la dimension no puede generar el bioma buscado (no esta entre los
 * biomas posibles de su BiomeSource), falla al momento en vez de recorrer
 * toda la espiral.
 *
 * Mientras busca, doWork() devuelve false: el WorldWorkerManager de Forge
 * pasa al siguiente tick sin gastar tiempo del servidor.
 *
 * Verificado por desensamblado del jar real. Revisadlo si se actualiza
 * Nature's Compass.
 */
@Pseudo
@Mixin(targets = "com.chaosthedude.naturescompass.util.BiomeSearchWorker", remap = false)
public abstract class BiomeSearchWorkerAsyncMixin {

    @Shadow(remap = false) private ServerLevel level;
    @Shadow(remap = false) private ResourceLocation biomeKey;
    @Shadow(remap = false) private BlockPos startPos;
    @Shadow(remap = false) private int samples;
    @Shadow(remap = false) private ItemStack stack;
    @Shadow(remap = false) private Player player;
    @Shadow(remap = false) private int x;
    @Shadow(remap = false) private int z;
    @Shadow(remap = false) private int[] yValues;
    @Shadow(remap = false) private boolean finished;
    @Shadow(remap = false) private int lastRadiusThreshold;
    @Shadow(remap = false) @Final private int sampleSpace;
    @Shadow(remap = false) @Final private int maxSamples;
    @Shadow(remap = false) @Final private int maxRadius;

    @Shadow(remap = false) private void succeed() { }
    @Shadow(remap = false) private void fail() { }
    @Shadow(remap = false) private int roundRadius(int radius, int roundTo) { return 0; }
    @Shadow(remap = false) public abstract boolean hasWork();

    @Unique
    private AsyncBiomeSearch.Job bteopt$job;

    @Inject(method = "doWork()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$doWorkAsync(CallbackInfoReturnable<Boolean> cir) {
        // Sin busqueda en marcha y sin trabajo: el original se encarga (llama a fail())
        if (bteopt$job == null && !hasWork()) return;
        if (finished) {
            cir.setReturnValue(false);
            return;
        }

        if (bteopt$job == null) {
            // Bioma que esta dimension no puede generar (p. ej. uno del Twilight Forest buscado en el
            // Overworld): el original recorreria toda la espiral (hasta maxSamples) para acabar fallando
            if (!AsyncBiomeSearch.canGenerate(level, biomeKey)) {
                samples = 0;
                fail();
                cir.setReturnValue(false);
                return;
            }
            bteopt$job = AsyncBiomeSearch.start(level, biomeKey, startPos, sampleSpace, maxSamples, maxRadius, yValues);
        }

        // Radio que muestra la brujula mientras busca: igual que el original, cada 500 bloques
        int radius = bteopt$job.radius();
        if (radius > 500 && radius / 500 > lastRadiusThreshold) {
            if (!stack.isEmpty() && stack.getItem() instanceof NaturesCompassItem compass) {
                compass.setSearchRadius(stack, roundRadius(radius, 500), player);
            }
            lastRadiusThreshold = radius / 500;
        }

        if (!bteopt$job.isDone()) {
            cir.setReturnValue(false); // aun buscando: no gastar mas tiempo de este tick
            return;
        }

        x = bteopt$job.x();
        z = bteopt$job.z();
        samples = bteopt$job.samples();
        if (bteopt$job.found()) succeed();
        else fail();
        cir.setReturnValue(false);
    }

    @Inject(method = "stop()V", at = @At("HEAD"), remap = false)
    private void bteopt$stop(CallbackInfo ci) {
        if (bteopt$job != null) bteopt$job.cancel();
    }
}
