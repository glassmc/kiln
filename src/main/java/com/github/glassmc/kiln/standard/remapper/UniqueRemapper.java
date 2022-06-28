package com.github.glassmc.kiln.standard.remapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.mapping.util.EntryTriple;

public class UniqueRemapper extends ReversibleRemapper {

    protected Map<String, String> classNames;
    protected Map<String, String> fieldNames;
    protected Map<String, String> methodNames;
    protected Map<String, String> variableNames;

    public UniqueRemapper() {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    public UniqueRemapper(Map<String, String> classNames, Map<String, String> fieldNames, Map<String, String> methodNames, Map<String, String> variableNames) {
        this.classNames = classNames;
        this.fieldNames = fieldNames;
        this.methodNames = methodNames;
        this.variableNames = variableNames;
    }

    @Override
    public String map(String name) {
        return classNames.getOrDefault(name, name);
    }

    @Override
    public String mapFieldName(final String owner, final String name, final String descriptor) {
        return fieldNames.getOrDefault(name, name);
    }

    @Override
    public String mapMethodName(final String owner, final String name, final String descriptor) {
        return methodNames.getOrDefault(name, name);
    }

    @Override
    public String mapVariableName(String clazz, String method, String methodDesc, String name, int index) {
        return variableNames.getOrDefault(name, name);
    }

    @Override
    public UniqueRemapper reversed() {
        Map<String, String> reverseClassNames = new HashMap<>();
        Map<String, String> reverseFieldNames = new HashMap<>();
        Map<String, String> reverseMethodNames = new HashMap<>();

        classNames.forEach((key, value) -> reverseClassNames.put(value, key));
        fieldNames.forEach((key, value) -> reverseFieldNames.put(value, key));
        methodNames.forEach((key, value) -> reverseMethodNames.put(value, key));

        return new UniqueRemapper(reverseClassNames, reverseFieldNames, reverseMethodNames, new HashMap<>());
    }

    public HashRemapper toNonUnique(HashRemapper from) {
        Map<EntryTriple, String> fieldNames = new HashMap<>();
        Map<EntryTriple, String> methodNames = new HashMap<>();

        Map<String, EntryTriple> fromFieldNamesReversed = new HashMap<>();
        Map<String, List<EntryTriple>> fromMethodNamesReversed = new HashMap<>();

        from.fieldNames.forEach((key, value) -> fromFieldNamesReversed.put(value, key));
        from.methodNames.forEach((key, value) -> fromMethodNamesReversed.computeIfAbsent(value, k -> new ArrayList<>()).add(key));

        this.fieldNames.forEach((key, value) -> {
            EntryTriple entry = fromFieldNamesReversed.get(key);

            if(entry == null) {
                return;
            }

            fieldNames.put(new EntryTriple(from.classNames.get(entry.getOwner()), key,
                    entry.getDescriptor().isEmpty() ? "" : from.mapDesc(entry.getDescriptor())), value);
        });

        this.methodNames.forEach((key, value) -> {
            for (EntryTriple entry : fromMethodNamesReversed.getOrDefault(key, new ArrayList<>())) {
                methodNames.put(new EntryTriple(from.classNames.get(entry.getOwner()), key, from.mapDesc(entry.getDescriptor())), value);
            }
        });

        return new HashRemapper(classNames, fieldNames, methodNames, new HashMap<>());
    }

    public Map<String, String> getClassNames() {
        return classNames;
    }

}
