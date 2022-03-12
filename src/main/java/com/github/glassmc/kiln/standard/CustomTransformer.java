package com.github.glassmc.kiln.standard;

import com.github.glassmc.kiln.standard.internalremapper.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.util.Map;

public abstract class CustomTransformer {

    private Remapper remapper;

    @SuppressWarnings("unused")
    public Remapper getRemapper() {
        return remapper;
    }

    public void setRemapper(Remapper remapper) {
        this.remapper = remapper;
    }

    public abstract void map(Map<String, ClassNode> classNodes);

}
