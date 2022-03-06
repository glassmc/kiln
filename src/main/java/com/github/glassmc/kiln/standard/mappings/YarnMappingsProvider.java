package com.github.glassmc.kiln.standard.mappings;

import com.github.glassmc.kiln.common.Pair;
import net.fabricmc.mapping.tree.*;
import org.apache.commons.io.FileUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import com.github.glassmc.kiln.standard.remapper.TinyRemapper;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class YarnMappingsProvider implements IMappingsProvider {

    // TODO Replace hard-coded build numbers, and scan maven-metadata.xml.
    private final Map<String, Pair<String, String>> mappings = new HashMap<String, Pair<String, String>>() {
        {
            put("1.7.10", Pair.of(
                    "https://maven.legacyfabric.net/net/fabricmc/intermediary/1.7.10/intermediary-1.7.10-v2.jar",
                    "https://maven.legacyfabric.net/net/fabricmc/yarn/1.7.10+build.202202221426/yarn-1.7.10+build.202202221426-v2.jar"
            ));
            put("1.8.9", Pair.of(
                    "https://maven.legacyfabric.net/net/fabricmc/intermediary/1.8.9/intermediary-1.8.9-v2.jar",
                    "https://maven.legacyfabric.net/net/fabricmc/yarn/1.8.9+build.202202221430/yarn-1.8.9+build.202202221430-v2.jar"
            ));
            put("1.12.2", Pair.of(
                    "https://maven.legacyfabric.net/net/fabricmc/intermediary/1.12.2/intermediary-1.12.2-v2.jar",
                    "https://maven.legacyfabric.net/net/fabricmc/yarn/1.12.2+build.202202221427/yarn-1.12.2+build.202202221427-v2.jar"
            ));
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
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.17.1+build.65/yarn-1.17.1+build.65-v2.jar"
            ));
            put("1.18", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.18/intermediary-1.18-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.18+build.1/yarn-1.18+build.1-v2.jar"
            ));
            put("1.18.1", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.18.1/intermediary-1.18.1-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.18.1+build.22/yarn-1.18.1+build.22-v2.jar"
            ));
            put("1.18.2", Pair.of(
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/1.18.1/intermediary-1.18.1-v2.jar",
                    "https://maven.fabricmc.net/net/fabricmc/yarn/1.18.2+build.2/yarn-1.18.2+build.2-v2.jar"
            ));
        }
    };

    private TinyTree namedTree;
    private TinyTree intermediaryTree;
    private Map<String, List<String>> parentClasses;
    private String version;

    @Override
    public void setup(File minecraftFile, String version) throws NoSuchMappingsException {
        this.version = version;

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
            // TODO log or wrap exception. IllegalStateException or Error should do fine.
            e.printStackTrace();
            return;
        }

        try {
            String intermediaryFileBase = intermediaryURL.getFile().substring(intermediaryURL.getFile().lastIndexOf("/")).substring(1).replace(".jar", "");
            String namedFileBase = namedURL.getFile().substring(namedURL.getFile().lastIndexOf("/")).substring(1).replace(".jar", "");

            File intermediaryMappings = new File(temp, intermediaryFileBase + ".tiny");
            File namedMappings = new File(temp, namedFileBase + ".tiny");

            if(!intermediaryMappings.exists() || !namedMappings.exists()) {
                File intermediaryMappingsFile = new File(temp, intermediaryFileBase + ".jar");
                File namedMappingsFile = new File(temp, namedFileBase + ".jar");
                FileUtils.copyURLToFile(namedURL, namedMappingsFile);
                FileUtils.copyURLToFile(intermediaryURL, intermediaryMappingsFile);

                JarFile intermediaryJARFile = new JarFile(intermediaryMappingsFile);
                FileUtils.copyInputStreamToFile(intermediaryJARFile.getInputStream(new ZipEntry("mappings/mappings.tiny")), intermediaryMappings);
                intermediaryJARFile.close();

                JarFile namedJARFile = new JarFile(namedMappingsFile);
                FileUtils.copyInputStreamToFile(namedJARFile.getInputStream(new ZipEntry("mappings/mappings.tiny")), namedMappings);
                namedJARFile.close();
            }

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
    public Remapper getRemapper(Direction direction) {
        String input = direction == Direction.TO_NAMED ? "official" : "named";
        String middle = "intermediary";
        String output = direction == Direction.TO_NAMED ? "named" : "official";

        Remapper initial = TinyRemapper.create(direction == Direction.TO_NAMED ? this.intermediaryTree : this.namedTree, input, middle);
        Remapper result = TinyRemapper.create(direction == Direction.TO_NAMED ? this.namedTree : this.intermediaryTree, middle, output);

        return new Remapper() {

            @Override
            public String map(String name) {
                return result.map(initial.map(name));
            }
            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                for(ClassDef classDef : getClasses(getObfName(owner, direction, initial, result), direction)) {
                    String middleName = classDef.getName(middle);
                    String initialName = classDef.getName(input);

                    String newName = result.mapMethodName(middleName, initial.mapMethodName(initialName, name, descriptor), initial.mapDesc(descriptor));
                    if(!newName.equals(name)) {
                        return newName;
                    }
                }
                return name;
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                for(ClassDef classDef : getClasses(getObfName(owner, direction, initial, result), direction)) {
                    String middleName = classDef.getName(middle);
                    String initialName = classDef.getName(input);

                    String newName = result.mapFieldName(middleName, initial.mapFieldName(initialName, name, ""), "");
                    if(!newName.equals(name)) {
                        return newName;
                    }
                }
                return name;
            }

        };
    }

    private List<ClassDef> getClasses(String obfName, Direction direction) {
        List<ClassDef> parents = new ArrayList<>();

        ClassDef classDef = this.intermediaryTree.getDefaultNamespaceClassMap().get(obfName);
        if(classDef != null) {
            ClassDef toAdd;
            if(direction == Direction.TO_NAMED) {
                toAdd = classDef;
            } else {
                toAdd = this.namedTree.getDefaultNamespaceClassMap().get(classDef.getName("intermediary"));
            }

            parents.add(toAdd);
        }

        if(parentClasses.get(obfName) != null) {
            for(String string : parentClasses.get(obfName)) {
                parents.addAll(this.getClasses(string, direction));
            }
        }

        return parents;
    }

    private String getObfName(String name, Direction direction, Remapper initial, Remapper result) {
        if(direction == Direction.TO_NAMED) {
            return name;
        } else if(direction == Direction.TO_OBFUSCATED) {
            return result.map(initial.map(name));
        }
        return name;
    }

    @Override
    public String getID() {
        return "yarn";
    }

    @Override
    public String getVersion() {
        return this.version;
    }

}
