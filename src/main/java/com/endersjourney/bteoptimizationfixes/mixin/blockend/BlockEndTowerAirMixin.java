package com.endersjourney.bteoptimizationfixes.mixin.blockend;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BlockEnd 1.3: StructureManager$Structure#placeComponentBatched se salta todos los bloques de aire de la
 * plantilla. En las de la torre (blockend:tower0 y blockend:tower1) ese aire es el hueco del ascensor y de su sala:
 * sin colocarlo, si el terreno del End (Nullscape) queda por encima, el ascensor sale enterrado en end stone.
 *
 * Aqui, solo para esas dos plantillas, el aire se coloca como cualquier otro bloque (como haria Minecraft con una
 * estructura normal), asi que excava el terreno dentro del volumen de la torre. El resto de plantillas (las islas)
 * siguen saltandose el aire como antes.
 *
 * placeComponentBatched solo llama una vez a BlockState#isAir (m_60795_), el filtro del aire.
 */
@Pseudo
@Mixin(targets = "fr.shoqapik.blockend.structures.StructureManager$Structure", remap = false)
public abstract class BlockEndTowerAirMixin {

    @Unique
    private ResourceLocation bteopt$currentTemplate;

    @Inject(method = "placeComponentBatched(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceLocation;III)Lnet/minecraft/core/Vec3i;",
            at = @At("HEAD"), remap = false, require = 0)
    private void bteopt$rememberTemplate(ServerLevel level, ResourceLocation location, int x, int y, int z,
                                         CallbackInfoReturnable<Vec3i> cir) {
        this.bteopt$currentTemplate = location;
    }

    @Redirect(method = "placeComponentBatched(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceLocation;III)Lnet/minecraft/core/Vec3i;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;m_60795_()Z", remap = false),
            remap = false, require = 0)
    private boolean bteopt$keepTowerAir(BlockState state) {
        if (bteopt$isTower(this.bteopt$currentTemplate)) {
            return false; // el aire de la torre tambien se coloca
        }
        return state.isAir();
    }

    @Unique
    private static boolean bteopt$isTower(ResourceLocation location) {
        if (location == null || !"blockend".equals(location.getNamespace())) return false;
        String path = location.getPath();
        return path.equals("tower0") || path.equals("tower1");
    }
}
