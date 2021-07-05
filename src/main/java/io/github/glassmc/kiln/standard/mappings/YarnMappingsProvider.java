package io.github.glassmc.kiln.standard.mappings;

import net.fabricmc.mapping.tree.*;
import org.apache.commons.io.FileUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class YarnMappingsProvider implements IMappingsProvider {

    private final Map<String, String> mappings = new HashMap<>() {
        {
            put("1.7.10", "https://maven.legacyfabric.net/net/fabricmc/yarn/1.7.10+build.202106280130/yarn-1.7.10+build.202106280130-mergedv2.jar");
            put("1.8.9", "https://maven.legacyfabric.net/net/fabricmc/yarn/1.8.9+build.202106280028/yarn-1.8.9+build.202106280028-mergedv2.jar");
            put("1.17", "https://maven.fabricmc.net/net/fabricmc/yarn/1.17+build.13/yarn-1.17+build.13-mergedv2.jar");
        }
    };

    private TinyTree tree;
    private Map<String, List<String>> parentClasses;

    @Override
    public void setup(File minecraftFile, String version) {
        File temp = new File(minecraftFile, "temp");
        try {
            URL mappingsURL = new URL(mappings.get(version));
            String mappingsFileBase = mappingsURL.getFile().substring(mappingsURL.getFile().lastIndexOf("/")).substring(1).replace(".jar", "");
            File mappingsFile = new File(temp, mappingsFileBase + ".jar");
            FileUtils.copyURLToFile(mappingsURL, mappingsFile);
            JarFile mappingsJARFile = new JarFile(mappingsFile);
            File mappings = new File(temp, mappingsFileBase + ".tiny");
            FileUtils.copyInputStreamToFile(mappingsJARFile.getInputStream(new ZipEntry("mappings/mappings.tiny")), mappings);

            this.tree = TinyMappingFactory.load(new BufferedReader(new FileReader(mappings)));

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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Remapper getRemapper(Direction direction) {
        String input = direction == Direction.TO_NAMED ? "official" : "named";
        String output = direction == Direction.TO_NAMED ? "named" : "official";

        return new Remapper() {
            @Override
            public String map(String name) {
                for(ClassDef classDef : tree.getClasses()) {
                    if(classDef.getName(input).equals(name)) {
                        return classDef.getName(output);
                    }
                }
                return name;
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                for(ClassDef classDef : tree.getClasses()) {
                    if(classDef.getName(input).equals(owner)) {
                        for(ClassDef parentDef : getClasses(classDef.getName("official"))) {
                            for(FieldDef fieldDef : parentDef.getFields()) {
                                if(fieldDef.getName(input).equals(name) && fieldDef.getDescriptor(input).equals(descriptor)) {
                                    return fieldDef.getName(output);
                                }
                            }
                        }
                    }
                }
                return name;
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                for(ClassDef classDef : tree.getClasses()) {
                    if(classDef.getName(input).equals(owner)) {
                        for(ClassDef parentDef : getClasses(classDef.getName("official"))) {
                            for(MethodDef methodDef : parentDef.getMethods()) {
                                if(methodDef.getName(input).equals(name) && methodDef.getDescriptor(input).equals(descriptor)) {
                                    return methodDef.getName(output);
                                }
                            }
                        }
                    }
                }
                return name;
            }

            @Override
            public Object mapValue(Object value) {
                if(value instanceof String) {
                    if(((String) value).contains("#") && ((String) value).length() >= 6) {
                        String[] classElementSplit = ((String) value).split("#");
                        for(ClassDef classDef : tree.getClasses()) {
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
                        for(ClassDef classDef : tree.getClasses()) {
                            if(classDef.getName(input).equals(value)) {
                                return classDef.getName(output);
                            }
                        }
                    }
                }
                return super.mapValue(value);
            }
        };
    }

    private List<ClassDef> getClasses(String name) {
        List<ClassDef> parents = new ArrayList<>();
        if(tree.getDefaultNamespaceClassMap().get(name) != null) {
            parents.add(tree.getDefaultNamespaceClassMap().get(name));
        }

        if(parentClasses.get(name) != null) {
            for(String string : parentClasses.get(name)) {
                parents.addAll(this.getClasses(string));
            }
        }

        return parents;
    }

    @Override
    public String getID() {
        return "yarn";
    }

}
