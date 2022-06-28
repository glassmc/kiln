package com.github.glassmc.kiln.standard;

import com.github.glassmc.kiln.common.Pair;
import com.github.glassmc.kiln.standard.internalremapper.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Map;

public abstract class CustomTransformer {

    private Remapper remapper;
    private Map<String, Pair<Map<String, String>, List<String>>> context;

    @SuppressWarnings("unused")
    public Remapper getRemapper() {
        return remapper;
    }

    public Map<String, Pair<Map<String, String>, List<String>>> getContext() {
        return context;
    }

    public void setRemapper(Remapper remapper) {
        this.remapper = remapper;
    }

    public void setContext(Map<String, Pair<Map<String, String>, List<String>>> context) {
        this.context = context;
    }

    public abstract void map(List<ClassNode> context, Map<String, ClassNode> classNodes);

}
