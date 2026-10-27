package com.endersjourney.bteoptimizationfixes.mixin.naturescompass;

import com.chaosthedude.naturescompass.util.BiomeUtils;
import com.endersjourney.bteoptimizationfixes.compat.naturescompass.AsyncBiomeSearch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Nature's Compass 1.19.2-1.10.0: al usar la brujula, el servidor manda al
 * cliente la lista de biomas que se pueden buscar (BiomeUtils#getAllowedBiomeKeys:
 * todos los del registro menos los de la lista negra), sin importar la
 * dimension. Aqui se quitan de esa lista los que la dimension del jugador no
 * puede generar, asi en el Overworld no aparecen los del Twilight Forest, del
 * Nether, etc.
 *
 * Si con el filtro no quedara ninguno (un generador raro que no informa de sus
 * biomas), se manda la lista original.
 *
 * m_7203_ = Item#use. Verificado por desensamblado del jar real.
 */
@Pseudo
@Mixin(targets = "com.chaosthedude.naturescompass.items.NaturesCompassItem", remap = false)
public abstract class CompassBiomeListDimensionMixin {

    @Redirect(
            method = "m_7203_",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/chaosthedude/naturescompass/util/BiomeUtils;getAllowedBiomeKeys(Lnet/minecraft/world/level/Level;)Ljava/util/List;",
                    remap = false
            ),
            remap = false
    )
    private List<ResourceLocation> bteopt$onlyThisDimension(Level level) {
        List<ResourceLocation> all = BiomeUtils.getAllowedBiomeKeys(level);
        if (!(level instanceof ServerLevel serverLevel)) return all;

        Set<ResourceLocation> possible = AsyncBiomeSearch.possibleBiomeKeys(serverLevel);
        List<ResourceLocation> filtered = new ArrayList<>(all.size());
        for (ResourceLocation key : all) {
            if (possible.contains(key)) filtered.add(key);
        }
        return filtered.isEmpty() ? all : filtered;
    }
}
