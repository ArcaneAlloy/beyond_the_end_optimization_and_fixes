package com.endersjourney.bteoptimizationfixes.mixin.geckolib;

import com.endersjourney.bteoptimizationfixes.compat.geckolib.client.ArmorLayerBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Guarda el buffer de dibujo con el que se esta pintando la capa de armaduras mientras dura, para que las armaduras
 * de GeckoLib lo usen (ver GeoArmorShadowBufferMixin). m_6494_ = HumanoidArmorLayer.render(...).
 */
@Mixin(value = HumanoidArmorLayer.class, remap = false)
public abstract class HumanoidArmorLayerSourceMixin {

    @Inject(method = "m_6494_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), remap = false)
    private void bteopt$pushBufferSource(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                         LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTick,
                                         float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        ArmorLayerBufferSource.push(bufferSource);
    }

    @Inject(method = "m_6494_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("RETURN"), remap = false)
    private void bteopt$popBufferSource(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                        LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTick,
                                        float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        ArmorLayerBufferSource.pop(bufferSource);
    }
}
