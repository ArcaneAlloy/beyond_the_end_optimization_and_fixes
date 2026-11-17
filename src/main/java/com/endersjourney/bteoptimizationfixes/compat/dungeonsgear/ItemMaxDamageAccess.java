package com.endersjourney.bteoptimizationfixes.compat.dungeonsgear;

/**
 * Lo implementa Item gracias a ItemMaxDamageAccessor: permite cambiar la durabilidad maxima (Item.maxDamage, final).
 * Lo usa UnbreakableArtifacts.
 */
public interface ItemMaxDamageAccess {

    void bteopt$setMaxDamage(int maxDamage);
}
