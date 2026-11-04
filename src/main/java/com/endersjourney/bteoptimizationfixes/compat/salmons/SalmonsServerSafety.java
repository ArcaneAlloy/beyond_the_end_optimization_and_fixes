package com.endersjourney.bteoptimizationfixes.compat.salmons;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Salmon's Genesis Reincarnation 1.1.8 no arranca en un servidor dedicado: usa código de cliente en dos sitios que
 * también se ejecutan en el servidor.
 *
 *  1. El constructor del mod hace MinecraftForge.EVENT_BUS.register(new ClientEvents()). ClientEvents usa clases de
 *     cliente (LocalPlayer, CameraType, renderers...) y al cargarla el servidor se cae
 *     ("Attempted to load class net/minecraft/client/player/LocalPlayer for invalid dist DEDICATED_SERVER").
 *     En el servidor se quita esa llamada.
 *
 *  2. ClientEvents además está anotada con @Mod.EventBusSubscriber(bus = MOD) sin indicar el lado, así que Forge la
 *     carga también en el servidor. Ahí dentro está entityAttributeEvent, que registra los atributos de los jefes del
 *     mod (Nether Warlord, etc.): el servidor la necesita. En el servidor se deja la clase solo con ese método (y su
 *     constructor) y se quita todo lo de cliente.
 *
 *  3. Al morir, el Nether Warlord (NetherWarlord.tickDeath) hace ((ClientEvents) PROXY) y escribe
 *     ClientEvents.renderNukeFlashFor (el destello de pantalla). En el servidor PROXY es un CommonProxy y el cast
 *     tumba el servidor (ClassCastException) justo al matar al jefe. En el servidor se quita el cast y la escritura;
 *     el destello lo sigue poniendo el propio cliente, que también ejecuta tickDeath.
 *
 * En el cliente (y en el servidor integrado de un jugador) no se toca nada.
 */
public final class SalmonsServerSafety {

    private SalmonsServerSafety() {
    }

    private static final Logger LOGGER = LogManager.getLogger("bte_optimization_fixes");
    private static final String CLIENT_EVENTS = "com/mod/salmonsgenesisreincarnation/server/events/ClientEvents";
    private static final Set<String> KEEP_METHODS = Set.of("<init>", "<clinit>", "entityAttributeEvent");

    private static boolean dedicatedServer() {
        return FMLEnvironment.dist == Dist.DEDICATED_SERVER;
    }

    private static AbstractInsnNode prevReal(AbstractInsnNode insn) {
        AbstractInsnNode p = insn.getPrevious();
        while (p != null && p.getOpcode() < 0) p = p.getPrevious();
        return p;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode n = insn.getNext();
        while (n != null && n.getOpcode() < 0) n = n.getNext();
        return n;
    }

    /** Quita MinecraftForge.EVENT_BUS.register(new ClientEvents()) del constructor del mod. */
    public static void patchMainClass(ClassNode node) {
        if (!dedicatedServer()) return;
        int removed = 0;
        for (MethodNode m : node.methods) {
            if (!m.name.equals("<init>")) continue;
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() != Opcodes.NEW || !CLIENT_EVENTS.equals(((TypeInsnNode) insn).desc)) continue;
                AbstractInsnNode bus = prevReal(insn);
                AbstractInsnNode dup = nextReal(insn);
                AbstractInsnNode ctor = dup == null ? null : nextReal(dup);
                AbstractInsnNode register = ctor == null ? null : nextReal(ctor);
                boolean ok = bus instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETSTATIC && f.name.equals("EVENT_BUS")
                        && dup != null && dup.getOpcode() == Opcodes.DUP
                        && ctor instanceof MethodInsnNode c && c.owner.equals(CLIENT_EVENTS) && c.name.equals("<init>")
                        && register instanceof MethodInsnNode r && r.name.equals("register");
                if (!ok) {
                    LOGGER.warn("[bte_optimization_fixes] Salmon's Genesis: el constructor no es el esperado, no se toca");
                    return;
                }
                AbstractInsnNode after = register.getNext();
                for (AbstractInsnNode n : new AbstractInsnNode[]{bus, insn, dup, ctor, register}) m.instructions.remove(n);
                removed++;
                insn = after == null ? m.instructions.getLast() : after;
            }
        }
        LOGGER.info("[bte_optimization_fixes] Salmon's Genesis: servidor dedicado, ClientEvents no se registra en el bus de Forge ({} llamada/s quitada/s)", removed);
    }

    /**
     * Quita los usos de ClientEvents desde código que también corre en el servidor: el cast
     * "GETSTATIC PROXY; CHECKCAST ClientEvents; POP" se elimina y cada "PUTSTATIC ClientEvents.campo" pasa a "POP"
     * (el valor ya está en la pila y se descarta).
     */
    public static void patchProxyCasts(ClassNode node) {
        if (!dedicatedServer()) return;
        int casts = 0, writes = 0;
        for (MethodNode m : node.methods) {
            List<AbstractInsnNode> drop = new ArrayList<>();
            List<AbstractInsnNode> toPop = new ArrayList<>();
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() == Opcodes.CHECKCAST && CLIENT_EVENTS.equals(((TypeInsnNode) insn).desc)) {
                    AbstractInsnNode prev = prevReal(insn);
                    AbstractInsnNode next = nextReal(insn);
                    if (prev instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETSTATIC && f.name.equals("PROXY")
                            && next != null && next.getOpcode() == Opcodes.POP) {
                        drop.add(prev);
                        drop.add(insn);
                        drop.add(next);
                        casts++;
                    }
                } else if (insn instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTSTATIC && f.owner.equals(CLIENT_EVENTS)
                        && (f.desc.equals("I") || f.desc.equals("F") || f.desc.equals("Z"))) {
                    toPop.add(insn);
                    writes++;
                }
            }
            for (AbstractInsnNode insn : drop) m.instructions.remove(insn);
            for (AbstractInsnNode insn : toPop) m.instructions.set(insn, new InsnNode(Opcodes.POP));
        }
        LOGGER.info("[bte_optimization_fixes] Salmon's Genesis: servidor dedicado, {}: {} cast(s) a ClientEvents y {} escritura(s) quitadas", node.name, casts, writes);
    }

    /** Deja ClientEvents solo con entityAttributeEvent (atributos de las entidades) y sin nada de cliente. */
    public static void patchClientEvents(ClassNode node) {
        if (!dedicatedServer()) return;
        int before = node.methods.size();
        node.methods.removeIf(m -> !KEEP_METHODS.contains(m.name));
        node.fields.removeIf(f -> f.desc.startsWith("Lnet/minecraft/client/") || f.desc.startsWith("Lcom/mojang/blaze3d/"));
        for (MethodNode m : node.methods) {
            if (!m.name.equals("<clinit>")) continue;
            List<AbstractInsnNode> drop = new ArrayList<>();
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof FieldInsnNode f && (f.desc.startsWith("Lnet/minecraft/client/") || f.owner.startsWith("net/minecraft/client/"))) {
                    drop.add(insn);
                }
            }
            for (AbstractInsnNode insn : drop) m.instructions.remove(insn);
        }
        LOGGER.info("[bte_optimization_fixes] Salmon's Genesis: servidor dedicado, ClientEvents reducida a los atributos de entidades ({} -> {} métodos)", before, node.methods.size());
    }
}
