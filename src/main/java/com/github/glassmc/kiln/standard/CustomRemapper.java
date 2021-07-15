package com.github.glassmc.kiln.standard;

import org.objectweb.asm.commons.Remapper;

public class CustomRemapper extends Remapper {

    private Remapper parent;

    public Remapper getParent() {
        return parent;
    }

    public void setParent(Remapper parent) {
        this.parent = parent;
    }

}
