package com.endersjourney.bteoptimizationfixes.mixin.dungeonsmobs;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib3.core.processor.IBone;

/**
 * Dungeons Mobs 4.0.8: el Skeleton Vanguard agarra la guja (dungeons_gear:glaive) de forma rara.
 *
 * Su renderer (GeckoLib) coloca lo que lleva en la mano derecha solo con un giro de -90 grados en X. Minecraft, en
 * cambio, al dibujar un objeto en la mano (ItemInHandLayer) hace ademas un giro de 180 grados en Y antes de aplicar la
 * pose de "tercera persona" del modelo del objeto. Las espadas son simetricas y no se nota, pero la guja tiene en su
 * modelo una pose inclinada 45 grados pensada para ese giro, asi que sin el sale hacia el lado contrario.
 *
 * Arreglo: para lo que lleva en la mano derecha (salvo escudos, que el renderer ya coloca a mano) se anade ese giro de
 * 180 grados en Y, como en vanilla.
 *
 * Verificado por desensamblado de dungeons_mobs-1.19.2-4.0.8-beta.jar (SkeletonVanguardRenderer.preRenderItem). Se
 * engancha al metodo puente (con LivingEntity), que llama al metodo real y se ejecuta siempre.
 * @Pseudo + remap = false: sin Dungeons Mobs el Mixin no se aplica.
 */
@Pseudo
@Mixin(targets = "com.infamous.dungeons_mobs.client.renderer.undead.SkeletonVanguardRenderer", remap = false)
public abstract class SkeletonVanguardHeldItemMixin {

    @Inject(method = "preRenderItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;Lnet/minecraft/world/entity/LivingEntity;Lsoftware/bernie/geckolib3/core/processor/IBone;)V",
            at = @At("TAIL"), remap = false, require = 0)
    private void bteopt$vanillaHeldItemRotation(PoseStack poseStack, ItemStack stack, String boneName, LivingEntity entity,
                                                IBone bone, CallbackInfo ci) {
        if (!"bipedHandRight".equals(boneName) || stack.isEmpty() || stack.getItem() instanceof ShieldItem) return;
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F));
    }
}
