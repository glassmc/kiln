package com.github.glassmc.kiln.standard.remapper;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.mapping.util.EntryTriple;

public class UniqueRemapper extends ReversibleRemapper {

    protected Map<String, String> classNames;
    protected Map<String, String> fieldNames;
    protected Map<String, String> methodNames;

    public UniqueRemapper() {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    public UniqueRemapper(Map<String, String> classNames, Map<String, String> fieldNames,
            Map<String, String> methodNames) {
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
        return fieldNames.getOrDefault(name, name);
    }

    @Override
    public String mapMethodName(final String owner, final String name, final String descriptor) {
        return methodNames.getOrDefault(name, name);
    }

    @Override
    public UniqueRemapper reversed() {
        Map<String, String> reverseClassNames = new HashMap<>();
        Map<String, String> reverseFieldNames = new HashMap<>();
        Map<String, String> reverseMethodNames = new HashMap<>();

        classNames.forEach((key, value) -> reverseClassNames.put(value, key));
        fieldNames.forEach((key, value) -> reverseFieldNames.put(value, key));
        methodNames.forEach((key, value) -> reverseMethodNames.put(value, key));

        return new UniqueRemapper(reverseClassNames, reverseFieldNames, reverseMethodNames);
    }

    public HashRemapper toNonUnique(HashRemapper from) {
        Map<EntryTriple, String> fieldNames = new HashMap<>();
        Map<EntryTriple, String> methodNames = new HashMap<>();

        Map<String, EntryTriple> fromFieldNamesReversed = new HashMap<>();
        Map<String, EntryTriple> fromMethodNamesReversed = new HashMap<>();

        from.fieldNames.forEach((key, value) -> fromFieldNamesReversed.put(value, key));
        from.methodNames.forEach((key, value) -> fromMethodNamesReversed.put(value, key));

        this.fieldNames.forEach((key, value) -> {
            EntryTriple entry = fromFieldNamesReversed.get(key);

            if(entry == null) {
                return;
            }

            fieldNames.put(new EntryTriple(from.classNames.get(entry.getOwner()), key,
                    entry.getDescriptor().isEmpty() ? "" : from.mapDesc(entry.getDescriptor())), value);
        });

        this.methodNames.forEach((key, value) -> {
            EntryTriple entry = fromMethodNamesReversed.get(key);

            if(entry == null) {
                return;
            }

            methodNames.put(new EntryTriple(from.classNames.get(entry.getOwner()), key, from.mapMethodDesc(entry.getDescriptor())),
                    value);
        });

        return new HashRemapper(classNames, fieldNames, methodNames);
    }

}