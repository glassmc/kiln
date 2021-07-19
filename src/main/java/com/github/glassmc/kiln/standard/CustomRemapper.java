package com.github.glassmc.kiln.standard;

import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.util.Map;

public class CustomRemapper {

    private Remapper parent;

    public Remapper getParent() {
        return parent;
    }

    public void setParent(Remapper parent) {
        this.parent = parent;
    }

    public void map(Map<String, ClassNode> classNode) {

    }

}
