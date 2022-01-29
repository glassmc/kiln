package com.github.glassmc.kiln.standard.remapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.mapping.util.EntryTriple;

public class HashRemapper extends ReversibleRemapper {

    protected Map<String, String> classNames;
    protected Map<EntryTriple, String> fieldNames;
    protected Map<EntryTriple, String> methodNames;

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

    @Override
    public HashRemapper reversed() {
        Map<String, String> reverseClassNames = new HashMap<>();
        Map<EntryTriple, String> reverseFieldNames = new HashMap<>();
        Map<EntryTriple, String> reverseMethodNames = new HashMap<>();

        classNames.forEach((key, value) -> reverseClassNames.put(value, key));

        fieldNames.forEach(
                        (key, value) -> reverseFieldNames.put(
                                new EntryTriple(map(key.getOwner()), value,
                                        key.getDescriptor().isEmpty() ? "" : mapDesc(key.getDescriptor())),
                                key.getName()));

        methodNames.forEach((key, value) -> reverseMethodNames.put(
                new EntryTriple(map(key.getOwner()), value, mapMethodDesc(key.getDescriptor())),
                key.getName()));

        return new HashRemapper(reverseClassNames, reverseFieldNames, reverseMethodNames);
    }

    public UniqueRemapper toUnique() {
        Map<String, String> fieldNames = new HashMap<>();
        Map<String, String> methodNames = new HashMap<>();

        this.fieldNames.forEach((key, value) -> {
            fieldNames.put(key.getName(), value);
        });
        this.methodNames.forEach((key, value) -> {
            methodNames.put(key.getName(), value);
        });

        return new UniqueRemapper(methodNames, fieldNames, methodNames);
    }

}
