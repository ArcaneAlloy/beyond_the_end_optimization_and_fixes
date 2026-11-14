package com.endersjourney.bteoptimizationfixes.compat.bettercombat.client;

import com.endersjourney.bteoptimizationfixes.BTEOptimizationFixes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Tooltip de las armas de Better Combat 1.7.1:
 * <ul>
 *   <li>Su línea de alcance ("2.75 Attack Range") lleva un espacio delante, la sangría de los atributos bajo "When in
 *   Main Hand:". Con las Improved Tooltips de Quark, los atributos se resumen en una línea de iconos y la de alcance se
 *   queda sola con ese espacio inútil: se quita mientras Quark esté resumiendo (con Mayús vuelven los atributos en
 *   texto y la sangría se mantiene para que quede alineada).</li>
 *   <li>Justo debajo del alcance se añade "One-Handed" o "Two-Handed", siempre (también sin Mayús).</li>
 *   <li>Se quita la línea "Two-Handed" que Better Combat añadía solo en la vista con Mayús, para que no salga dos
 *   veces.</li>
 * </ul>
 * Better Combat y Quark se consultan por reflexión: sin ellos esto no hace nada.
 */
@Mod.EventBusSubscriber(modid = BTEOptimizationFixes.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HandednessTooltip {

    private static final String RANGE_NAME = "attribute.name.generic.attack_range";
    private static final String BC_TWO_HANDED = "item.modifiers.two_handed";

    private static boolean looked = false;
    private static Method getAttributes = null;   // WeaponRegistry.getAttributes(ItemStack)
    private static Method isTwoHanded = null;     // WeaponAttributes.isTwoHanded()
    private static Method quarkHides = null;      // AttributeTooltips.shouldHideAttributes()

    private HandednessTooltip() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltip(ItemTooltipEvent event) {
        lookup();
        if (getAttributes == null) return;
        Boolean twoHanded = twoHanded(event.getItemStack());
        if (twoHanded == null) return;   // no es un arma de Better Combat

        List<Component> lines = event.getToolTip();
        lines.removeIf(HandednessTooltip::isBetterCombatTwoHanded);

        boolean compact = quarkCompactsAttributes();
        int rangeIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (isAttackRangeLine(lines.get(i))) {
                rangeIndex = i;
                if (compact) lines.set(i, withoutLeadingSpace(lines.get(i)));
                break;
            }
        }

        Component hand = Component.translatable(twoHanded
                ? "tooltip.bte_optimization_fixes.two_handed"
                : "tooltip.bte_optimization_fixes.one_handed").withStyle(ChatFormatting.GRAY);
        // Debajo del alcance; si no hay línea de alcance, debajo del nombre
        int at = rangeIndex >= 0 ? rangeIndex + 1 : Math.min(1, lines.size());
        lines.add(at, hand);
    }

    /** true / false si es un arma de Better Combat (a dos manos o no), null si no lo es. */
    private static Boolean twoHanded(ItemStack stack) {
        if (stack.isEmpty()) return null;
        try {
            Object attributes = getAttributes.invoke(null, stack);
            if (attributes == null) return null;
            return (Boolean) isTwoHanded.invoke(attributes);
        } catch (Throwable t) {
            return null;
        }
    }

    /** ¿Quark está resumiendo los atributos en su línea de iconos (Improved Tooltips activas y sin Mayús)? */
    private static boolean quarkCompactsAttributes() {
        if (quarkHides == null) return false;
        try {
            return (Boolean) quarkHides.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** La línea " 2.75 Attack Range" de Better Combat: un literal con un atributo traducido cuyo nombre es el alcance. */
    private static boolean isAttackRangeLine(Component line) {
        if (isRangeModifier(line)) return true;
        for (Component sibling : line.getSiblings()) {
            if (isRangeModifier(sibling)) return true;
        }
        return false;
    }

    private static boolean isRangeModifier(Component c) {
        if (!(c.getContents() instanceof TranslatableContents tc) || !tc.getKey().startsWith("attribute.modifier.")) return false;
        for (Object arg : tc.getArgs()) {
            if (arg instanceof Component a && a.getContents() instanceof TranslatableContents name && RANGE_NAME.equals(name.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBetterCombatTwoHanded(Component line) {
        return line.getContents() instanceof TranslatableContents tc && BC_TWO_HANDED.equals(tc.getKey());
    }

    /** La misma línea sin el literal de sangría del principio (conservando estilo y color). */
    private static Component withoutLeadingSpace(Component line) {
        if (!(line.getContents() instanceof LiteralContents literal) || !literal.text().isBlank() || line.getSiblings().isEmpty()) {
            return line;
        }
        MutableComponent copy = Component.empty().setStyle(line.getStyle());
        for (Component sibling : line.getSiblings()) copy.append(sibling);
        return copy;
    }

    private static void lookup() {
        if (looked) return;
        looked = true;
        try {
            Class<?> registry = Class.forName("net.bettercombat.logic.WeaponRegistry");
            getAttributes = registry.getMethod("getAttributes", ItemStack.class);
            isTwoHanded = Class.forName("net.bettercombat.api.WeaponAttributes").getMethod("isTwoHanded");
        } catch (Throwable t) {
            getAttributes = null;
            isTwoHanded = null;
        }
        try {
            quarkHides = Class.forName("vazkii.quark.content.client.tooltip.AttributeTooltips").getMethod("shouldHideAttributes");
        } catch (Throwable t) {
            quarkHides = null;
        }
    }
}
