package com.github.glassmc.kiln.standard.remapper;

import org.objectweb.asm.commons.Remapper;

public abstract class ReversibleRemapper extends Remapper {

    public abstract ReversibleRemapper reversed();

}
