package com.github.glassmc.kiln.standard.mappings;

import com.github.glassmc.kiln.common.Pair;
import net.fabricmc.mapping.tree.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import com.github.glassmc.kiln.standard.remapper.TinyRemapper;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class YarnMappingsProvider implements IMappingsProvider {

    private TinyTree namedTree;
    private TinyTree intermediaryTree;
    private Map<String, List<String>> parentClasses;
    private String version;

    @Override
    public void setup(File minecraftFile, String version) throws NoSuchMappingsException {
        this.version = version;

        File mappingsJson = new File(minecraftFile.getParentFile().getParentFile(), "mappings.json");
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(FileUtils.readFileToString(mappingsJson, StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }

        JSONObject mappings = jsonObject.getJSONObject("yarn").getJSONObject(version);

        if (mappings == null) {
            throw new NoSuchMappingsException(version);
        }

        File temp = new File(minecraftFile, "temp");
        Pair<String, String> mappingURLs = new Pair<>(mappings.getString("intermediary"), mappings.getString("named"));

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

    @Override
    public void clearCache(File minecraftFile) {
        File temp = new File(minecraftFile, "temp");
        try {
            if (temp.exists()) {
                FileUtils.cleanDirectory(temp);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
