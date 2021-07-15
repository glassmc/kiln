package com.github.glassmc.kiln.standard.mappings;

import net.fabricmc.mapping.tree.*;
import net.fabricmc.mapping.util.EntryTriple;
import org.apache.commons.io.FileUtils;
import org.gradle.internal.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class YarnMappingsProvider implements IMappingsProvider {

    private final Map<String, Pair<String, String>> mappings = new HashMap<String, Pair<String, String>>() {
        {
            put("1.7.10", null);
            put("1.8.9", null);
            put("1.12.2", null);
            put("1.14", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.14/intermediary-1.14-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.14+build.21/yarn-1.14+build.21-v2.jar"
            ));
            put("1.14.1", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.14.1/intermediary-1.14.1-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.14.1+build.10/yarn-1.14.1+build.10-v2.jar"
            ));
            put("1.14.2", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.14.2/intermediary-1.14.2-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.14.2+build.7/yarn-1.14.2+build.7-v2.jar"
            ));
            put("1.14.3", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.14.3/intermediary-1.14.3-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.14.3+build.13/yarn-1.14.3+build.13-v2.jar"
            ));
            put("1.14.4", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.14.4/intermediary-1.14.4-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.14.4+build.13/yarn-1.14.4+build.18-v2.jar"
            ));
            put("1.15", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.15/intermediary-1.15-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.15+build.2/yarn-1.15+build.2-v2.jar"
            ));
            put("1.15.1", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.15.1/intermediary-1.15.1-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.15.1+build.37/yarn-1.15.1+build.37-v2.jar"
            ));
            put("1.15.2", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.15.2/intermediary-1.15.2-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.15.2+build.17/yarn-1.15.2+build.17-v2.jar"
            ));
            put("1.16", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.16/intermediary-1.16-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.16+build.4/yarn-1.16+build.4-v2.jar"
            ));
            put("1.16.1", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.16.1/intermediary-1.16.1-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.16.1+build.21/yarn-1.16.1+build.21-v2.jar"
            ));
            put("1.16.2", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.16.2/intermediary-1.16.2-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.16.2+build.47/yarn-1.16.2+build.47-v2.jar"
            ));
            put("1.16.3", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.16.3/intermediary-1.16.3-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.16.3+build.47/yarn-1.16.3+build.47-v2.jar"
            ));
            put("1.16.4", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.16.4/intermediary-1.16.4-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.16.4+build.9/yarn-1.16.4+build.9-v2.jar"
            ));
            put("1.16.5", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.16.5/intermediary-1.16.5-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.16.5+build.10/yarn-1.16.5+build.10-v2.jar"
            ));
            put("1.17", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.17/intermediary-1.17-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.17+build.13/yarn-1.17+build.13-v2.jar"
            ));
            put("1.17.1", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.17.1/intermediary-1.17.1-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.17.1+build.10/yarn-1.17.1+build.10-v2.jar"
            ));
        }
    };

    private TinyTree namedTree;
    private TinyTree intermediaryTree;
    private Map<String, List<String>> parentClasses;

    @Override
    public void setup(File minecraftFile, String version) throws NoSuchMappingsException {
        File temp = new File(minecraftFile, "temp");
        Pair<String, String> mappingURLs = mappings.get(version);

        if(mappingURLs == null) {
            throw new NoSuchMappingsException(version);
        }

        URL intermediaryURL;
        URL namedURL;

        try {
            intermediaryURL = new URL(Objects.requireNonNull(mappingURLs.getLeft()));
            namedURL = new URL(Objects.requireNonNull(mappingURLs.getRight()));
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return;
        }

        try {
            String intermediaryFileBase = intermediaryURL.getFile().substring(intermediaryURL.getFile().lastIndexOf("/")).substring(1).replace(".jar", "");
            String namedFileBase = namedURL.getFile().substring(namedURL.getFile().lastIndexOf("/")).substring(1).replace(".jar", "");
            File intermediaryMappingsFile = new File(temp, intermediaryFileBase + ".jar");
            File namedMappingsFile = new File(temp, namedFileBase + ".jar");
            FileUtils.copyURLToFile(namedURL, namedMappingsFile);
            FileUtils.copyURLToFile(intermediaryURL, intermediaryMappingsFile);

            JarFile intermediary = new JarFile(intermediaryMappingsFile);
            File intermediaryMappings = new File(temp, intermediaryFileBase + ".tiny");
            FileUtils.copyInputStreamToFile(intermediary.getInputStream(new ZipEntry("mappings/mappings.tiny")), intermediaryMappings);
            JarFile namedJARFile = new JarFile(namedMappingsFile);
            File namedMappings = new File(temp, namedFileBase + ".tiny");
            FileUtils.copyInputStreamToFile(namedJARFile.getInputStream(new ZipEntry("mappings/mappings.tiny")), namedMappings);

            this.intermediaryTree = TinyMappingFactory.load(new BufferedReader(new FileReader(intermediaryMappings)));
            this.namedTree = TinyMappingFactory.load(new BufferedReader(new FileReader(namedMappings)));

            this.parentClasses = new HashMap<>();
            JarFile jarFile = new JarFile(new File(minecraftFile, "client-" + version + ".jar"));
            Enumeration<JarEntry> entries = jarFile.entries();
            while(entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if(entry.getName().endsWith(".class")) {
                    ClassNode classNode = new ClassNode();
                    ClassReader classReader = new ClassReader(jarFile.getInputStream(entry));
                    classReader.accept(classNode, 0);

                    List<String> parents = parentClasses.computeIfAbsent(classNode.name, k -> new ArrayList<>());
                    parents.add(classNode.superName);
                    parents.addAll(classNode.interfaces);
                }
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void destroy() {
        this.intermediaryTree = null;
        this.parentClasses = null;
    }

    @Override
    public Remapper getRemapper(Direction direction) {
        String input = direction == Direction.TO_NAMED ? "official" : "named";
        String middle = "intermediary";
        String output = direction == Direction.TO_NAMED ? "named" : "official";

        TinyRemapper intermediary = new TinyRemapper(this.intermediaryTree, input, middle);
        TinyRemapper result = new TinyRemapper(this.namedTree, middle, output);

        return new Remapper() {

            @Override
            public String map(String internalName) {
                return result.map(intermediary.map(internalName));
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                for(ClassDef classDef : getClasses(owner)) {
                    String newName = result.mapMethodName(classDef.getName(middle), intermediary.mapMethodName(classDef.getName(input), name, descriptor), intermediary.mapDesc(descriptor));
                    if(!newName.equals(name)) {
                        return newName;
                    }
                }
                return name;
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                for(ClassDef classDef : getClasses(owner)) {
                    String newName = result.mapFieldName(classDef.getName(middle), intermediary.mapFieldName(classDef.getName(input), name, descriptor), intermediary.mapDesc(descriptor));
                    if(!newName.equals(name)) {
                        return newName;
                    }
                }
                return name;
            }

        };
    }

    private List<ClassDef> getClasses(String name) {
        List<ClassDef> parents = new ArrayList<>();
        if(intermediaryTree.getDefaultNamespaceClassMap().get(name) != null) {
            parents.add(intermediaryTree.getDefaultNamespaceClassMap().get(name));
        }

        if(parentClasses.get(name) != null) {
            for(String string : parentClasses.get(name)) {
                parents.addAll(this.getClasses(string));
            }
        }

        return parents;
    }

    private static class TinyRemapper extends Remapper {

        private final Map<String, String> classNames = new HashMap<>();
        private final Map<EntryTriple, String> fieldNames = new HashMap<>();
        private final Map<EntryTriple, String> methodNames = new HashMap<>();

        private TinyRemapper(TinyTree tree, String from, String to) {
            for (ClassDef clazz : tree.getClasses()) {
                String className = clazz.getName(from);
                classNames.put(className, clazz.getName(to));
                for(FieldDef field : clazz.getFields()) {
                    fieldNames.put(new EntryTriple(className, field.getName(from), field.getDescriptor(from)), field.getName(to));
                }
                for(MethodDef method : clazz.getMethods()) {
                    methodNames.put(new EntryTriple(className, method.getName(from), method.getDescriptor(from)), method.getName(to));
                }
            }
        }

        @Override
        public String map(String typeName) {
            return classNames.getOrDefault(typeName, typeName);
        }

        @Override
        public String mapFieldName(final String owner, final String name, final String descriptor) {
            return fieldNames.getOrDefault(new EntryTriple(owner, name, descriptor), name);
        }

        @Override
        public String mapMethodName(final String owner, final String name, final String descriptor) {
            return methodNames.getOrDefault(new EntryTriple(owner, name, descriptor), name);
        }

    }

    @Override
    public String getID() {
        return "yarn";
    }

    /*@Override
            public Object mapValue(Object value) {
                if(value instanceof String) {
                    if(((String) value).contains("#") && ((String) value).length() >= 6) {
                        String[] classElementSplit = ((String) value).split("#");
                        for(ClassDef classDef : intermediaryTree.getClasses()) {
                            if(classDef.getName(input).equals(classElementSplit[0])) {
                                if(classElementSplit[1].contains("(")) {
                                    String[] methodDescriptorSplit = classElementSplit[1].split("\\(");
                                    methodDescriptorSplit[1] = "(" + methodDescriptorSplit[1];
                                    for(MethodDef methodDef : classDef.getMethods()) {
                                        if(methodDef.getName(input).equals(methodDescriptorSplit[0]) && methodDef.getDescriptor(input).equals(methodDescriptorSplit[1])) {
                                            return classDef.getName(output) + "#" + methodDef.getName(output) + methodDef.getDescriptor(output);
                                        }
                                    }
                                } else {
                                    for(FieldDef fieldDef : classDef.getFields()) {
                                        if(fieldDef.getName(input).equals(classElementSplit[1])) {
                                            return classDef.getName(output) + "#" + fieldDef.getName(output);
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        for(ClassDef classDef : intermediaryTree.getClasses()) {
                            if(classDef.getName(input).equals(value)) {
                                return classDef.getName(output);
                            }
                        }
                    }
                }*/

}
