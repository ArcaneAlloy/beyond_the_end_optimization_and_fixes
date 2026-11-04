package com.endersjourney.bteoptimizationfixes;

import com.endersjourney.bteoptimizationfixes.compat.salmons.SalmonsServerSafety;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Plugin de la configuración de Mixins. Aplica todos los Mixins como siempre; solo lo usan los arreglos que necesitan
 * cambiar el bytecode de una clase antes de que se aplique su Mixin (ver SalmonsServerSafety).
 */
public class BteMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.endsWith(".salmons.SalmonsMainServerSafeMixin")) {
            SalmonsServerSafety.patchMainClass(targetClass);
        } else if (mixinClassName.endsWith(".salmons.SalmonsClientEventsServerSafeMixin")) {
            SalmonsServerSafety.patchClientEvents(targetClass);
        } else if (mixinClassName.endsWith(".salmons.SalmonsWarlordServerSafeMixin")) {
            SalmonsServerSafety.patchProxyCasts(targetClass);
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
