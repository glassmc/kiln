package com.github.glassmc.kiln.standard.remmaper;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.mapping.util.EntryTriple;

public class TinyRemapper {

    public static HashRemapper create(TinyTree tree, String from, String to) {
        Map<String, String> classNames = new HashMap<>();
        Map<EntryTriple, String> fieldNames = new HashMap<>();
        Map<EntryTriple, String> methodNames = new HashMap<>();

        for(ClassDef clazz : tree.getClasses()) {
            String classNameFrom = clazz.getName(from);
            String classNameTo = clazz.getName(to);

            classNames.put(classNameFrom, classNameTo);

            for(FieldDef field : clazz.getFields()) {
                fieldNames.put(new EntryTriple(classNameFrom, field.getName(from), ""), field.getName(to));
            }
            for(MethodDef method : clazz.getMethods()) {
                methodNames.put(new EntryTriple(classNameFrom, method.getName(from), method.getDescriptor(from)),
                        method.getName(to));
            }
        }

        return new HashRemapper(classNames, fieldNames, methodNames);
    }

}