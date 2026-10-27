package com.endersjourney.bteoptimizationfixes.mixin.uselessmilk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.PrintStream;

/**
 * Useless Milk 1.0: UselessMilk#eatMilk escucha LivingEntityUseItemEvent.Tick y
 * hace System.out.println(event.getDuration()) en CADA tick en que un jugador
 * usa CUALQUIER objeto (comer, beber, tensar un arco, cubrirse con el escudo...),
 * en cliente y en servidor. Por eso el log se llena de numeros que cuentan hacia
 * atras (32, 32, 31, 31... al comer). Aqui solo se quita ese println; el resto
 * del metodo (leche, batidos) sigue igual.
 *
 * Verificado por desensamblado del jar real (uselessmilk-1.0.jar).
 */
@Pseudo
@Mixin(targets = "fr.shoqapik.uselessmilk.UselessMilk", remap = false)
public abstract class UselessMilkTickLogMixin {

    @Redirect(
            method = "eatMilk",
            at = @At(value = "INVOKE", target = "Ljava/io/PrintStream;println(I)V", remap = false),
            remap = false
    )
    private void bteopt$noTickLog(PrintStream out, int duration) {
        // nada: era un log de depuracion
    }
}
