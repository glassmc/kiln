package com.github.glassmc.kiln.mappings;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.github.glassmc.kiln.Pair;
import com.github.glassmc.kiln.Util;
import net.fabricmc.mapping.util.EntryTriple;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import com.github.glassmc.kiln.internalremapper.Remapper;

import com.github.glassmc.kiln.remapper.CSRGRemapper;
import com.github.glassmc.kiln.remapper.CSVRemapper;
import com.github.glassmc.kiln.remapper.HashRemapper;
import com.github.glassmc.kiln.remapper.ReversibleRemapper;
import com.github.glassmc.kiln.remapper.UniqueRemapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public class MCPMappingsProvider implements IMappingsProvider {

    private String version;
    private String mappingsVersion;

    private HashRemapper searge;
    private ReversibleRemapper reversedSearge;
    private UniqueRemapper named;
    private ReversibleRemapper reversedNamed;

    private Map<String, List<String>> parentClasses;

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

        JSONArray mappings = jsonObject.getJSONArray("mcp");

        File temp = new File(minecraftFile, "temp");

        boolean found = false;
        URL seargeURL = null;
        URL mcpURL = null;
        for (Object possibleURL : mappings) {
            JSONObject jsonObject1 = (JSONObject) possibleURL;

            try {
                seargeURL = new URL(jsonObject1.getString("srg").replace("%version%", version).replace("%mappingsVersion%", mappingsVersion));
                mcpURL = new URL(jsonObject1.getString("mcp").replace("%version%", version).replace("%mappingsVersion%", mappingsVersion));

                if (Util.isValid(seargeURL) && Util.isValid(mcpURL)) {
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

        //Pair<String, String> mappingURLs = new Pair<>(mappings.getString("srg"), mappings.getString("mcp"));

        /*try {
            seargeURL = new URL(Objects.requireNonNull(mappingURLs.getLeft()));
            mcpURL = new URL(Objects.requireNonNull(mappingURLs.getRight()));
        } catch(MalformedURLException e) {
            e.printStackTrace();
            return;
        }*/

        try {
            String seargeFileBase = seargeURL.getFile().substring(seargeURL.getFile().lastIndexOf("/")).substring(1).replace(".zip", "");
            String namedFileBase = mcpURL.getFile().substring(mcpURL.getFile().lastIndexOf("/")).substring(1).replace(".zip", "");

            File seargeMappings = new File(temp, seargeFileBase + "-joined.csrg");
            File seargeMappingsParams = new File(temp, seargeFileBase + "-joined.exc");
            File staticMethods = new File(temp, seargeFileBase + "-static_methods.txt");
            File fieldMappings = new File(temp, namedFileBase + "-fields.csv");
            File methodMappings = new File(temp, namedFileBase + "-methods.csv");
            File paramMappings = new File(temp, namedFileBase + "-params.csv");

            if(!seargeMappings.exists() || !fieldMappings.exists()) {
                File seargeMappingsFile = new File(temp, seargeFileBase + ".zip");
                File namedMappingsFile = new File(temp, namedFileBase + ".zip");
                FileUtils.copyURLToFile(mcpURL, namedMappingsFile);
                FileUtils.copyURLToFile(seargeURL, seargeMappingsFile);

                ZipFile seargeZIPFile = new ZipFile(seargeMappingsFile);
                FileUtils.copyInputStreamToFile(seargeZIPFile.getInputStream(new ZipEntry("joined.csrg")), seargeMappings);
                FileUtils.copyInputStreamToFile(seargeZIPFile.getInputStream(new ZipEntry("joined.exc")), seargeMappingsParams);
                FileUtils.copyInputStreamToFile(seargeZIPFile.getInputStream(new ZipEntry("static_methods.txt")), staticMethods);
                seargeZIPFile.close();

                JarFile namedJARFile = new JarFile(namedMappingsFile);
                FileUtils.copyInputStreamToFile(namedJARFile.getInputStream(new ZipEntry("fields.csv")), fieldMappings);
                FileUtils.copyInputStreamToFile(namedJARFile.getInputStream(new ZipEntry("methods.csv")), methodMappings);
                FileUtils.copyInputStreamToFile(namedJARFile.getInputStream(new ZipEntry("params.csv")), paramMappings);
                namedJARFile.close();
            }

            searge = CSRGRemapper.create(seargeMappings, seargeMappingsParams);
            reversedSearge = searge.reversed();
            named = CSVRemapper.create(null, fieldMappings, methodMappings, paramMappings, "searge", "param", "name");
            reversedNamed = named.toNonUnique(searge).reversed();

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
        ReversibleRemapper initial = direction == Direction.TO_NAMED ? searge : reversedNamed;
        ReversibleRemapper result = direction == Direction.TO_NAMED ? named : reversedSearge;

        return new Remapper() {

            @Override
            public String map(String name) {
                return result.map(initial.map(name));
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                for(String className : getClasses(getObfName(owner, direction, this), direction, initial, result)) {
                    String newName = result.mapMethodName(initial.map(className), initial.mapMethodName(className, name, descriptor), initial.mapDesc(descriptor));
                    if(!newName.equals(name)) {
                        return newName;
                    }
                }
                return name;
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                for(String className : getClasses(getObfName(owner, direction, this), direction, initial, result)) {
                    String newName = result.mapFieldName(initial.map(className), initial.mapFieldName(className, name, descriptor), initial.mapDesc(descriptor));
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

            private final List<String> staticMethods = new ArrayList<>();

            @Override
            public String mapVariableName(String clazz, String method, String methodDesc, String name, int index) {
                String newName = initial.mapVariableName(clazz, method, methodDesc, name, index);
                String methodName = initial.mapMethodName(clazz, method, methodDesc);

                String fullIdentifier = clazz + "#" + method + methodDesc;
                if (index == 0 && !name.equals("this")) {
                    staticMethods.add(fullIdentifier);
                }

                if (newName.equals(name) && !name.equals("this") && methodName.startsWith("func_") && index < Type.getMethodType(methodDesc).getArgumentTypes().length + (staticMethods.contains(fullIdentifier) ? 0 : 1)) {
                    newName = "p_" + methodName.substring(methodName.indexOf("_") + 1, methodName.lastIndexOf("_")) + "_" + index + "_";
                }

                return result.mapVariableName(initial.map(clazz), methodName, initial.mapMethodDesc(methodDesc), newName, index);
            }

        };
    }

    @Override
    public Map<String, Pair<Map<String, String>, List<String>>> getContext(Side side, boolean prefix) {
        if (side == Side.NAMED) {
            Remapper remapper = this.getRemapper(Direction.TO_NAMED);

            Map<String, Pair<Map<String, String>, List<String>>> context = new HashMap<>();

            for (Map.Entry<String, String> className : searge.getClassNames().entrySet()) {
                String className1 = className.getValue();

                context.put(className1, new Pair<>(new HashMap<>(), new ArrayList<>()));
            }

            for (Map.Entry<EntryTriple, String> methodName : searge.getMethodNames().entrySet()) {
                String className = remapper.map(methodName.getKey().getOwner());
                String methodName1 = named.mapMethodName(className, methodName.getValue(), null);
                String methodDesc = remapper.mapMethodDesc(methodName.getKey().getDescriptor());
                context.get(className).getLeft().put(methodName1, methodDesc);
            }

            return context;
        }
        return null;
    }

    private List<String> getClasses(String obfName, Direction direction, ReversibleRemapper initial, ReversibleRemapper result) {
        List<String> parents = new ArrayList<>();

        if (direction == Direction.TO_NAMED) {
            parents.add(obfName);
        } else {
            parents.add(initial.reversed().map(result.reversed().map(obfName)));
        }

        if(parentClasses.get(obfName) != null) {
            for(String string : parentClasses.get(obfName)) {
                parents.addAll(this.getClasses(string, direction, initial, result));
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
        return "mcp";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getMappingsVersion() {
        return mappingsVersion;
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
