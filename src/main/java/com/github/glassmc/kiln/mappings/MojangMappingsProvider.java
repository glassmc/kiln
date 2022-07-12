package com.github.glassmc.kiln.mappings;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.github.glassmc.kiln.Pair;
import com.github.glassmc.kiln.remapper.HashRemapper;
import net.fabricmc.mapping.util.EntryTriple;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import com.github.glassmc.kiln.internalremapper.Remapper;

import com.github.glassmc.kiln.Util;
import com.github.glassmc.kiln.remapper.ProGuardRemapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

//TODO: Some kind of mojang-parchment mappings to get parameter remapping
public class MojangMappingsProvider implements IMappingsProvider {

    private String version;
    private HashRemapper deobfuscator;
    private HashRemapper obfuscator;

    private Map<String, List<String>> parentClasses;

    @Override
    public void setup(File minecraftFile, String version, String mappingsVersion) throws NoSuchMappingsException {
        try {
            this.version = version;

            File temp = new File(minecraftFile, "temp");

            JSONObject versionManifest = Util.getVersionManifest(version);

            JSONObject downloads = versionManifest.getJSONObject("downloads");

            // Use client-sided mappings as they contain both sides.
            if(!downloads.has("client_mappings")) {
                throw new NoSuchMappingsException(version);
            }

            URL mappingsURL = new URL(downloads.getJSONObject("client_mappings").getString("url"));

            File mappingsFile = new File(temp, "mojang-mappings-" + version + ".txt");
            FileUtils.copyURLToFile(mappingsURL, mappingsFile);

            obfuscator = ProGuardRemapper.create(mappingsFile);
            deobfuscator = obfuscator.reversed();

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
        } catch(IOException error) {
            error.printStackTrace();
        }
    }

    @Override
    public Remapper getRemapper(Direction direction) {
        Remapper remapper;
        Remapper reverseRemapper;
        if (direction == Direction.TO_NAMED) {
            remapper = deobfuscator;
            reverseRemapper = obfuscator;
        } else {
            remapper = obfuscator;
            reverseRemapper = deobfuscator;
        }

        return new Remapper() {

            @Override
            public String map(String name) {
                return remapper.map(name);
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                for(String className : getClasses(getObfName(owner, direction, remapper), direction, reverseRemapper)) {
                    String newName = remapper.mapMethodName(className, name, descriptor);
                    if(!newName.equals(name)) {
                        return newName;
                    }
                }
                return name;
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                for(String className : getClasses(getObfName(owner, direction, remapper), direction, reverseRemapper)) {
                    String newName = remapper.mapFieldName(className, name, descriptor);
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
                return remapper.mapVariableName(owner, methodOwner, methodDesc, name, index);
            }

        };
    }

    @Override
    public Map<String, Pair<Map<String, String>, List<String>>> getContext(Side side, boolean prefix) {
        if (side == Side.NAMED) {
            Remapper remapper = this.getRemapper(Direction.TO_NAMED);

            Map<String, Pair<Map<String, String>, List<String>>> context = new HashMap<>();

            for (Map.Entry<String, String> className : deobfuscator.getClassNames().entrySet()) {
                String className1 = className.getValue();

                context.put(className1, new Pair<>(new HashMap<>(), new ArrayList<>()));
            }

            for (Map.Entry<EntryTriple, String> methodName : deobfuscator.getMethodNames().entrySet()) {
                String className = remapper.map(methodName.getKey().getOwner());
                String methodDesc = remapper.mapMethodDesc(methodName.getKey().getDescriptor());
                String methodName1 = deobfuscator.mapMethodName(className, methodName.getValue(), methodDesc);
                context.get(className).getLeft().put(methodName1, methodDesc);
            }

            return context;
        }
        return null;
    }

    private List<String> getClasses(String obfName, Direction direction, Remapper reverseRemapper) {
        List<String> parents = new ArrayList<>();

        if (direction == Direction.TO_NAMED) {
            parents.add(obfName);
        } else {
            parents.add(reverseRemapper.map(obfName));
        }

        if(parentClasses.get(obfName) != null) {
            for(String string : parentClasses.get(obfName)) {
                parents.addAll(this.getClasses(string, direction, reverseRemapper));
            }
        }

        return parents;
    }

    private String getObfName(String name, Direction direction, Remapper remapper) {
        if(direction == Direction.TO_NAMED) {
            return name;
        } else if(direction == Direction.TO_OBFUSCATED) {
            return remapper.map(name);
        }
        return name;
    }

    @Override
    public String getID() {
        return "mojang";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getMappingsVersion() {
        return null;
    }

    @Override
    public void clearCache(File minecraftFile) {

    }

}
