package com.github.glassmc.kiln.mappings;

import com.github.glassmc.kiln.Pair;
import com.github.glassmc.kiln.Util;
import net.fabricmc.mapping.tree.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.objectweb.asm.ClassReader;
import com.github.glassmc.kiln.internalremapper.Remapper;
import org.objectweb.asm.tree.ClassNode;

import com.github.glassmc.kiln.remapper.TinyRemapper;

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
    private String mappingsVersion;

    @Override
    public void setup(File minecraftFile, String version, String mappingsVersion) throws NoSuchMappingsException {
        this.version = version;
        this.mappingsVersion = mappingsVersion;

        File mappingsJson = new File(minecraftFile.getParentFile().getParentFile(), "mappings.json");
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(FileUtils.readFileToString(mappingsJson, StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }

        JSONArray mappings = jsonObject.getJSONArray("yarn");

        boolean found = false;
        URL intermediaryURL = null;
        URL namedURL = null;
        File temp = new File(minecraftFile, "temp");
        for (Object possibleURL : mappings) {
            JSONObject jsonObject1 = (JSONObject) possibleURL;

            try {
                intermediaryURL = new URL(jsonObject1.getString("intermediary").replace("%version%", version).replace("%mappingsVersion%", mappingsVersion));
                namedURL = new URL(jsonObject1.getString("named").replace("%version%", version).replace("%mappingsVersion%", mappingsVersion));

                if (Util.isValid(intermediaryURL) && Util.isValid(namedURL)) {
                    found = true;
                    break;
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        if (!found) {
            throw new NoSuchMappingsException(version + "-" + mappingsVersion);
        }

        try {
            String intermediaryFileBase = intermediaryURL.getFile().substring(intermediaryURL.getFile().lastIndexOf("/")).substring(1).replace(".jar", "") + "-" + mappingsVersion;
            String namedFileBase = namedURL.getFile().substring(namedURL.getFile().lastIndexOf("/")).substring(1).replace(".jar", "") + "-" + mappingsVersion;

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
                    if (!classNode.superName.equals("java/lang/Object")) {
                        parents.add(classNode.superName);
                    }
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

            @Override
            public String mapRecordComponentName(String owner, String name, String descriptor) {
                return this.mapFieldName(owner, name, descriptor);
            }

            @Override
            public String mapVariableName(String owner, String methodOwner, String methodDesc, String name, int index) {
                return result.mapVariableName(initial.map(owner), initial.mapMethodName(owner, methodOwner, methodDesc), initial.mapDesc(methodDesc), name, index);
            }

        };
    }

    @Override
    public Map<String, Pair<Map<String, String>, List<String>>> getContext(Side side, boolean prefix) {
        if (side == Side.NAMED) {
            Map<String, Pair<Map<String, String>, List<String>>> context = new HashMap<>();

            Remapper remapperObf = this.getRemapper(Direction.TO_OBFUSCATED);
            Remapper obfToIntermediary = TinyRemapper.create(this.intermediaryTree, "official", "intermediary");

            for (ClassDef classDef : namedTree.getClasses()) {
                Pair<Map<String, String>, List<String>> pair = new Pair<>(new HashMap<>(), new ArrayList<>());
                addAllMethods(namedTree, classDef, pair.getLeft(), remapperObf, obfToIntermediary);
                context.put(classDef.getName("named"), pair);
            }

            return context;
        }
        return null;
    }

    private void addAllMethods(TinyTree tinyTree, ClassDef classDef, Map<String, String> methods, Remapper remapperObf, Remapper obfToIntermediary) {
        for (MethodDef methodDef : classDef.getMethods()) {
            methods.put(methodDef.getName("named"), methodDef.getDescriptor("named"));
        }

        if (this.parentClasses.get(remapperObf.map(classDef.getName("named"))) != null) {
            for (String parentClass : this.parentClasses.get(remapperObf.map(classDef.getName("named")))) {
                ClassDef parentClassDef = tinyTree.getDefaultNamespaceClassMap().get(obfToIntermediary.map(parentClass));
                if (parentClassDef != null) {
                    addAllMethods(tinyTree, parentClassDef, methods, remapperObf, obfToIntermediary);
                }
            }
        }
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
    public String getMappingsVersion() {
        return this.mappingsVersion;
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
