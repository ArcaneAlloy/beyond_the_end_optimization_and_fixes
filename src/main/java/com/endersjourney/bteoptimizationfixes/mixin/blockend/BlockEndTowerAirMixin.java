package com.endersjourney.bteoptimizationfixes.mixin.blockend;

import com.endersjourney.bteoptimizationfixes.worldgen.BlockEndTowerCarve;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Queue;

/**
 * BlockEnd 1.3: StructureManager$Structure#placeComponentBatched se salta todos los bloques de aire de la
 * plantilla. En las de la torre (blockend:tower0 y blockend:tower1) parte de ese aire es el hueco del ascensor y de
 * su sala: sin colocarlo, si el terreno del End (Nullscape) queda por encima, el ascensor sale enterrado.
 *
 * Colocar TODO el aire de la plantilla vaciaba la caja entera (61x108x58 y 65x198x59) y dejaba cortes rectos en el
 * terreno. Aqui solo se excava el aire "interior" de la torre: en cada columna (x, z) de la plantilla, el aire que
 * queda entre el bloque solido mas bajo y el mas alto de esa misma columna (suelo de la torre debajo y torre
 * encima: sala y hueco del ascensor). Las columnas sin torre, o el espacio bajo los arcos, no se tocan.
 *
 * Las tareas de excavado se meten en la cola de BlockEnd al empezar a procesar la plantilla, justo antes de sus
 * propias tareas de colocacion. Ver BlockEndTowerCarve.
 */
@Pseudo
@Mixin(targets = "fr.shoqapik.blockend.structures.StructureManager$Structure", remap = false)
public abstract class BlockEndTowerAirMixin {

    @Shadow(remap = false)
    public Queue<Runnable> tasks;

    @Inject(method = "placeComponentBatched(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceLocation;III)Lnet/minecraft/core/Vec3i;",
            at = @At("HEAD"), remap = false, require = 0)
    private void bteopt$carveTowerInterior(ServerLevel level, ResourceLocation location, int x, int y, int z,
                                           CallbackInfoReturnable<Vec3i> cir) {
        BlockEndTowerCarve.queueCarve(this.tasks, level, location, x, y, z);
    }
}
