package com.github.glassmc.kiln.standard.remapper;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.commons.Remapper;

import net.fabricmc.mapping.util.EntryTriple;

public class HashRemapper extends Remapper {

    protected Map<String, String> classNames;
    protected Map<EntryTriple, String> fieldNames;
    protected Map<EntryTriple, String> methodNames;

    public HashRemapper() {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    public HashRemapper(Map<String, String> classNames, Map<EntryTriple, String> fieldNames,
            Map<EntryTriple, String> methodNames) {
        this.classNames = classNames;
        this.fieldNames = fieldNames;
        this.methodNames = methodNames;
    }

    @Override
    public String map(String name) {
        return classNames.getOrDefault(name, name);
    }

    @Override
    public String mapFieldName(final String owner, final String name, final String descriptor) {
        return fieldNames.getOrDefault(new EntryTriple(owner, name, ""), name);
    }

    @Override
    public String mapMethodName(final String owner, final String name, final String descriptor) {
        return methodNames.getOrDefault(new EntryTriple(owner, name, descriptor), name);
    }

    public HashRemapper reversed() {
        Map<String, String> reverseClassNames = new HashMap<>();
        Map<EntryTriple, String> reverseFieldNames = new HashMap<>();
        Map<EntryTriple, String> reverseMethodNames = new HashMap<>();

        classNames.forEach((key, value) -> {
            reverseClassNames.put(value, key);
        });

        fieldNames.forEach((key, value) -> {
            reverseFieldNames.put(
                    new EntryTriple(classNames.get(key.getOwner()), value, remapDescriptor(key.getDescriptor())),
                    key.getName());
        });

        methodNames.forEach((key, value) -> {
            reverseMethodNames.put(
                    new EntryTriple(classNames.get(key.getOwner()), value, remapFullDescriptor(key.getDescriptor())),
                    key.getName());
        });

        return new HashRemapper(reverseClassNames, reverseFieldNames, reverseMethodNames);
    }

    private String remapDescriptor(String descriptor) {
        if(descriptor.startsWith("L") && descriptor.endsWith(";")) {
            String className = descriptor.substring(1, descriptor.length() - 1);

            descriptor = "L" + map(className) + ";";
        }

        return descriptor;
    }

    private String remapFullDescriptor(String descriptor) {
        StringBuilder classBuilder = null;
        StringBuilder result = new StringBuilder();

        for(char c : descriptor.toCharArray()) {
            if(c == ';' && classBuilder != null) {
                result.append(remapDescriptor("L" + classBuilder.toString() + ";"));
                classBuilder = null;
            } else if(c == 'L' && classBuilder == null) {
                classBuilder = new StringBuilder();
            } else {
                if(classBuilder != null) {
                    classBuilder.append(c);
                }
                else {
                    result.append(c);
                }
            }
        }

        return result.toString();
    }

}
