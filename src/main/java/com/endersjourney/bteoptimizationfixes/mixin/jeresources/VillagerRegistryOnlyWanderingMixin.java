package com.endersjourney.bteoptimizationfixes.mixin.jeresources;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * En el modpack no se puede comerciar con los aldeanos (el clic derecho esta deshabilitado); los unicos tradeos que
 * existen son los del Wandering Trader. Just Enough Resources mostraba igualmente en JEI ("Villager Trades") los
 * tradeos de todas las profesiones de aldeano.
 *
 * JER 1.2.3: VillagersHelper#initRegistry anade una VillagerEntry por profesion y una WanderingTraderEntry, todas con
 * VillagerRegistry#addVillagerEntry. Aqui se descartan las VillagerEntry y solo queda la del Wandering Trader.
 *
 * Verificado por desensamblado de JustEnoughResources-1.19.2-1.2.3.243.jar.
 * @Pseudo + remap = false: sin Just Enough Resources el Mixin no se aplica.
 */
@Pseudo
@Mixin(targets = "jeresources.registry.VillagerRegistry", remap = false)
public abstract class VillagerRegistryOnlyWanderingMixin {

    private static final String BTEOPT$VILLAGER_ENTRY = "jeresources.entry.VillagerEntry";

    @Inject(method = "addVillagerEntry", at = @At("HEAD"), cancellable = true, remap = false)
    private void bteopt$onlyWanderingTrader(@Coerce Object entry, CallbackInfo ci) {
        if (entry != null && BTEOPT$VILLAGER_ENTRY.equals(entry.getClass().getName())) {
            ci.cancel();
        }
    }
}
