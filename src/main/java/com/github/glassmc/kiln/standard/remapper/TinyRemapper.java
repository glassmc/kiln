package com.github.glassmc.kiln.standard.remapper;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.mapping.tree.*;
import net.fabricmc.mapping.util.EntryTriple;

public class TinyRemapper {

    public static HashRemapper create(TinyTree tree, String from, String to) {
        Map<String, String> classNames = new HashMap<>();
        Map<EntryTriple, String> fieldNames = new HashMap<>();
        Map<EntryTriple, String> methodNames = new HashMap<>();
        Map<EntryTriple, Map<Integer, String>> variableNames = new HashMap<>();

        for(ClassDef clazz : tree.getClasses()) {
            String classNameFrom = clazz.getName(from);
            String classNameTo = clazz.getName(to);

            classNames.put(classNameFrom, classNameTo);

            for(FieldDef field : clazz.getFields()) {
                fieldNames.put(new EntryTriple(classNameFrom, field.getName(from), ""), field.getName(to));
            }
            for(MethodDef method : clazz.getMethods()) {
                methodNames.put(new EntryTriple(classNameFrom, method.getName(from), method.getDescriptor(from)), method.getName(to));

                for (ParameterDef parameter : method.getParameters()) {
                    variableNames.computeIfAbsent(new EntryTriple(classNameFrom, method.getName(from), method.getDescriptor(from)), k -> new HashMap<>()).put(parameter.getLocalVariableIndex(), parameter.getName(to));
                }
            }
        }

        return new HashRemapper(classNames, fieldNames, methodNames, variableNames);
    }

}