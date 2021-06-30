package io.github.glassmc.kiln.standard.mappings;

import net.fabricmc.mapping.tree.*;
import org.apache.commons.io.FileUtils;
import org.objectweb.asm.commons.Remapper;

import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class YarnMappingsProvider implements IMappingsProvider {

    private final Map<String, String> mappings = new HashMap<String, String>() {
        {
            put("1.7.10", "https://maven.legacyfabric.net/net/fabricmc/yarn/1.7.10+build.202106280130/yarn-1.7.10+build.202106280130-mergedv2.jar");
            put("1.8.9", "https://maven.legacyfabric.net/net/fabricmc/yarn/1.8.9+build.202106280028/yarn-1.8.9+build.202106280028-mergedv2.jar");
        }
    };

    private TinyTree tree;

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
                        for(FieldDef fieldDef : classDef.getFields()) {
                            if(fieldDef.getName(input).equals(name) && fieldDef.getDescriptor(input).equals(descriptor)) {
                                return fieldDef.getName(output);
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
                        for(MethodDef methodDef : classDef.getMethods()) {
                            if(methodDef.getName(input).equals(name) && methodDef.getDescriptor(input).equals(descriptor)) {
                                return methodDef.getName(output);
                            }
                        }
                    }
                }
                return name;
            }
        };
    }

    @Override
    public String getID() {
        return "yarn";
    }

}
