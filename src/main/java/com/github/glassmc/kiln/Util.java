package com.github.glassmc.kiln;

import com.github.glassmc.kiln.mappings.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
//import org.jboss.windup.decompiler.api.DecompilationListener;
//import org.jboss.windup.decompiler.fernflower.FernflowerDecompiler;
//import org.jboss.windup.decompiler.util.Filter;
import org.json.JSONException;
import org.json.JSONObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import com.github.glassmc.kiln.internalremapper.ClassRemapper;
import com.github.glassmc.kiln.internalremapper.Remapper;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class Util {

    private static JSONObject versions;
    private static final Map<String, JSONObject> versionsById = new HashMap<>();

    public static void minecraft(String id, String version, String mappingsProviderId, boolean prefix, boolean runtime) {
        KilnPlugin plugin = KilnPlugin.getInstance();
        File pluginCache = plugin.getCache();

        IMappingsProvider mappingsProvider;
        switch(mappingsProviderId) {
            case "yarn":
                mappingsProvider = new YarnMappingsProvider();
                break;
            case "mojang":
                mappingsProvider = new MojangMappingsProvider();
                break;
            case "mcp":
                mappingsProvider = new MCPMappingsProvider();
                break;
            case "obfuscated":
            default:
                mappingsProvider = new ObfuscatedMappingsProvider();
        }
        plugin.addMappingsProvider(mappingsProvider, false);

        File minecraftFile = new File(pluginCache, "minecraft");
        File versionFile = new File(minecraftFile, version);

        try {
            FileUtils.copyURLToFile(new URL("https://raw.githubusercontent.com/glassmc/data/main/kiln/mappings.json"), new File(pluginCache, "mappings.json"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Util.setupMinecraft(id, version, pluginCache, new ObfuscatedMappingsProvider(), runtime);

        try {
            mappingsProvider.setup(versionFile, version);
        } catch (NoSuchMappingsException e) {
            e.printStackTrace();
        }

        Util.setupMinecraft(id, version, pluginCache, mappingsProvider, runtime);
    }

    public static void setupMinecraft(String environment, String version, File pluginCache, IMappingsProvider mappingsProvider, boolean runtime) {
        File minecraftFile = new File(pluginCache, "minecraft");
        File versionFile = new File(minecraftFile, version);
        File versionJARFile = new File(versionFile, environment + "-" + version + ".jar");
        File localMaven = new File(minecraftFile, "localMaven");
        File versionMappedJARFile = new File(localMaven, "net/minecraft/" + environment + "-" + version + "/" + mappingsProvider.getID() + "/" + environment + "-" + version + "-" + mappingsProvider.getID() + ".jar");

        try {
            JSONObject versionManifest = getVersionManifest(version);

            List<String> prefixClasses = new ArrayList<>();
            List<String[]> dependencies = new ArrayList<>();

            if(environment.equals("client")) {
                File versionLibraries = new File(versionFile, "libraries");
                File versionNatives = new File(versionFile, "natives");
                File assets = new File(versionFile, "assets");

                if (!versionLibraries.exists()) {
                    System.out.printf("Downloading %s libraries...%n", version);
                    downloadLibraries(versionManifest, versionLibraries);
                    mapLibraries(versionManifest, versionLibraries, localMaven, version);
                }

                if (!versionNatives.exists()) {
                    System.out.printf("Downloading %s natives...%n", version);
                    downloadNatives(versionManifest, versionNatives);
                }

                if (!assets.exists() && runtime) {
                    System.out.printf("Downloading %s assets...%n", version);
                    downloadAssets(versionManifest, assets);
                }

                Map<String, String> libraries = new HashMap<>();

                for (Object library : versionManifest.getJSONArray("libraries")) {
                    JSONObject libraryJSON = (JSONObject) library;
                    JSONObject downloads = libraryJSON.getJSONObject("downloads");
                    if (downloads.has("artifact")) {
                        String url = downloads.getJSONObject("artifact").getString("url");
                        url = url.substring(url.lastIndexOf("/") + 1);

                        String id2 = libraryJSON.getString("name");

                        libraries.put(url, id2);
                    }
                }

                for (File mappedLibrary : Objects.requireNonNull(versionLibraries.listFiles())) {
                    if (!mappedLibrary.getName().endsWith(".jar")) continue;

                    JarFile jarFile = new JarFile(mappedLibrary);
                    Enumeration<JarEntry> entries = jarFile.entries();

                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (entry.getName().endsWith(".class")) {
                            prefixClasses.add(entry.getName().replace(".class", ""));
                        }
                    }

                    dependencies.add(libraries.get(mappedLibrary.getName()).split(":"));
                }
            }

            if (!versionMappedJARFile.exists()) {
                if (!versionJARFile.exists()) {
                    System.out.printf("Downloading %s jar...%n", version);
                    URL versionJarURL = new URL(versionManifest.getJSONObject("downloads").getJSONObject(environment).getString("url"));
                    FileUtils.copyURLToFile(versionJarURL, versionJARFile);
                }

                JarFile input = new JarFile(versionJARFile);

                System.out.printf("Remapping %s jar with %s mappings...%n", version, mappingsProvider.getID());

                Remapper remapper = mappingsProvider.getRemapper(IMappingsProvider.Direction.TO_NAMED);

                Remapper remapperWrapper = new Remapper() {

                    @Override
                    public String map(String name) {
                        String mapped = remapper.map(name);
                        if ((input.getJarEntry(name + ".class") != null || prefixClasses.contains(mapped)) && mappingsProvider.getVersion() != null) {
                            return mapped;
                        } else {
                            return name;
                        }
                    }

                    @Override
                    public String mapFieldName(String owner, String name, String descriptor) {
                        return remapper.mapFieldName(owner, name, descriptor);
                    }

                    @Override
                    public String mapRecordComponentName(String owner, String name, String descriptor) {
                        return remapper.mapRecordComponentName(owner, name, descriptor);
                    }

                    @Override
                    public String mapMethodName(String owner, String name, String descriptor) {
                        return remapper.mapMethodName(owner, name, descriptor);
                    }

                    @Override
                    public String mapVariableName(String clazz, String method, String methodDesc, String name, int index) {
                        return remapper.mapVariableName(clazz, method, methodDesc, name, index);
                    }

                };

                versionMappedJARFile.getParentFile().mkdirs();
                JarOutputStream outputStream = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(versionMappedJARFile)));

                Enumeration<JarEntry> entries = input.entries();
                while(entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if(!entry.isDirectory()) {
                        if(entry.getName().endsWith(".class")) {
                            ClassReader classReader = new ClassReader(IOUtils.readFully(input.getInputStream(entry), (int) entry.getSize()));
                            ClassWriter writer = new ClassWriter(0);
                            ClassVisitor visitor = new ClassRemapper(writer, remapperWrapper);
                            classReader.accept(visitor, 0);

                            outputStream.putNextEntry(new JarEntry(remapperWrapper.map(entry.getName().replace(".class", "")) + ".class"));
                            outputStream.write(writer.toByteArray());
                            outputStream.closeEntry();
                        } else if(!entry.getName().contains("META-INF")) {
                            outputStream.putNextEntry(new JarEntry(entry.getName()));
                            outputStream.write(IOUtils.readFully(input.getInputStream(entry), (int) entry.getSize()));
                            outputStream.closeEntry();
                        }
                    }
                }
                outputStream.close();

                File versionPom = new File(versionMappedJARFile.getParentFile(), environment + "-" + version + "-" + mappingsProvider.getID() + ".pom");
                StringBuilder string =
                        new StringBuilder(
                                "<project>\n" +
                                        "    <modelVersion>4.0.0</modelVersion>\n" +
                                        "\n" +
                                        "    <groupId>net.minecraft</groupId>\n" +
                                        "    <artifactId>" + environment + "-" + version + "</artifactId>\n" +
                                        "    <version>" + mappingsProvider.getID() + "</version>\n" +
                                        "    <dependencies>\n");

                for (String[] dependency : dependencies) {
                    string.append("        <dependency>\n" + "            <groupId>").append(dependency[0]).append("</groupId>\n").append("            <artifactId>").append(dependency[1]).append("-").append(version).append("</artifactId>\n").append("            <version>").append(dependency[2]).append("</version>\n").append("        </dependency>\n");
                }

                string.append("    </dependencies>\n" + "</project>\n");

                FileUtils.writeStringToFile(versionPom, string.toString(), StandardCharsets.UTF_8);
            } else if (runtime) {
                File assets = new File(versionFile, "assets");

                if (!assets.exists()) {
                    System.out.printf("Downloading %s assets...%n", version);
                    downloadAssets(versionManifest, assets);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void mapLibraries(JSONObject versionManifest, File versionLibraries, File localMaven, String version) throws IOException {
        Map<String, String> libraries = new HashMap<>();

        for (Object library : versionManifest.getJSONArray("libraries")) {
            JSONObject libraryJSON = (JSONObject) library;
            JSONObject downloads = libraryJSON.getJSONObject("downloads");
            if (downloads.has("artifact")) {
                String url = downloads.getJSONObject("artifact").getString("url");
                url = url.substring(url.lastIndexOf("/") + 1);

                String id = libraryJSON.getString("name");

                libraries.put(url, id);
            }
        }

        for (File library : Objects.requireNonNull(versionLibraries.listFiles())) {
            if (!library.getName().endsWith(".jar")) continue;

            String[] id = libraries.get(library.getName()).split(":");
            File file = new File(localMaven, id[0].replace(".", "/") + "/" + id[1] + "-" + version + "/" + id[2] + "/" + id[1] + "-" + version + "-" + id[2] + ".jar");
            file.getParentFile().mkdirs();

            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(file));

            JarFile jarFile = new JarFile(library);
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    jarOutputStream.putNextEntry(new JarEntry(entry.getName().replace(".class", "") + ".class"));
                    jarOutputStream.write(IOUtils.readFully(jarFile.getInputStream(entry), (int) entry.getSize()));
                    jarOutputStream.closeEntry();
                } else {
                    jarOutputStream.putNextEntry(new JarEntry(entry.getName()));
                    InputStream inputStream = jarFile.getInputStream(entry);
                    jarOutputStream.write(IOUtils.readFully(inputStream, inputStream.available()));
                    jarOutputStream.closeEntry();
                }
            }

            jarOutputStream.close();

            File versionPom = new File(file.getParentFile(), file.getName().replace(".jar", ".pom"));
            FileUtils.writeStringToFile(versionPom, "<project>\n" +
                    "    <modelVersion>4.0.0</modelVersion>\n" +
                    "\n" +
                    "    <groupId>" + id[0] + "</groupId>\n" +
                    "    <artifactId>" + id[1] + "-" + version + "</artifactId>\n" +
                    "    <version>" + id[2] + "</version>\n" +
                    "</project>\n", StandardCharsets.UTF_8);
        }
    }

    private static void downloadAssets(JSONObject versionManifest, File assets) {
        String id = versionManifest.getJSONObject("assetIndex").getString("id");
        String assetIndexUrl = versionManifest.getJSONObject("assetIndex").getString("url");
        try {
            File file = new File(assets, "indexes/" + id + ".json");
            file.getParentFile().mkdirs();
            FileUtils.copyURLToFile(new URL(assetIndexUrl), file);

            String fileContents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(fileContents);

            JSONObject objects = json.getJSONObject("objects");

            File objectsFile = new File(assets, "objects");
            objectsFile.mkdirs();

            int i = 0;

            int previousPercent = 0;

            String url = "http://resources.download.minecraft.net";
            for (Map.Entry<String, Object> entry : objects.toMap().entrySet()) {
                Map<String, Object> value = (Map<String, Object>) entry.getValue();
                String hash = (String) value.get("hash");
                FileUtils.copyURLToFile(new URL(url + "/" + hash.substring(0, 2) + "/" + hash), new File(objectsFile, hash.substring(0, 2) + "/" + hash));

                double percent = (double) i / objects.length();
                if (Math.ceil(percent * 100) > previousPercent) {
                    StringBuilder stringBuilder = new StringBuilder("[");
                    for (int j = 0; j <  (int) Math.ceil(20 * percent); j++) {
                        stringBuilder.append("=");
                    }
                    for (int j = (int) Math.ceil(20 * percent); j < 20; j++) {
                        stringBuilder.append(" ");
                    }
                    stringBuilder.append("] ").append((int) Math.ceil(percent * 100)).append("%\r");

                    System.out.write(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
                    System.out.flush();

                    previousPercent = (int) Math.ceil(percent * 100);
                }
                i++;
            }

            System.out.println();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static JSONObject getVersionManifest(String id) throws JSONException, IOException {
        if(versions == null) {
            URL versionsURL = new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json");
            versions = new JSONObject(IOUtils.toString(versionsURL, StandardCharsets.UTF_8));
        }

        if(versionsById.containsKey(id)) {
            return versionsById.get(id);
        }

        JSONObject versionInfo = new JSONObject();
        for(Object info : versions.getJSONArray("versions")) {
            if(((JSONObject) info).getString("id").equals(id)) {
                versionInfo = (JSONObject) info;
            }
        }

        String versionManifestString = versionInfo.getString("url");
        URL versionManifestURL = new URL(versionManifestString);
        JSONObject versionManifest = new JSONObject(IOUtils.toString(versionManifestURL, StandardCharsets.UTF_8));

        versionsById.put(id, versionManifest);

        return versionManifest;
    }

    private static void downloadLibraries(JSONObject versionManifest, File versionLibraries) throws IOException {
        for (Object element : versionManifest.getJSONArray("libraries")) {
            JSONObject library = (JSONObject) element;

            JSONObject downloads = library.getJSONObject("downloads");
            if (!downloads.has("artifact")) {
                continue;
            }

            boolean allowed = true;

            if (library.has("rules")) {
                allowed = false;

                String osName = "";
                switch (SystemUtil.getOSType()) {
                    case WINDOWS:
                        osName = "windows";
                        break;
                    case LINUX:
                        osName = "linux";
                        break;
                    case MAC:
                        osName = "osx";
                        break;
                    case UNKNOWN:
                        break;
                }

                for (Object item : library.getJSONArray("rules")) {
                    JSONObject rule = (JSONObject) item;
                    if (!rule.has("os") || (rule.has("os") && rule.getJSONObject("os").getString("name").equals(osName))) {
                        allowed = rule.getString("action").equals("allow");
                    }
                }
            }

            if (allowed) {
                String artifactURL = downloads.getJSONObject("artifact").getString("url");
                File libraryFile = new File(versionLibraries, artifactURL.substring(artifactURL.lastIndexOf("/") + 1));
                FileUtils.copyURLToFile(
                        new URL(artifactURL),
                        libraryFile
                );
            }
        }
    }

    private static void downloadNatives(JSONObject versionManifest, File versionNatives) throws IOException {
        for(Object element : versionManifest.getJSONArray("libraries")) {
            JSONObject library = (JSONObject) element;

            if(library.has("natives")) {
                boolean allowed = true;
                if (library.has("rules")) {
                    allowed = false;

                    String osName = "";
                    switch (SystemUtil.getOSType()) {
                        case WINDOWS:
                            osName = "windows";
                            break;
                        case LINUX:
                            osName = "linux";
                            break;
                        case MAC:
                            osName = "osx";
                            break;
                        case UNKNOWN:
                            break;
                    }

                    for (Object item : library.getJSONArray("rules")) {
                        JSONObject rule = (JSONObject) item;
                        if (!rule.has("os") || (rule.has("os") && rule.getJSONObject("os").getString("name").equals(osName))) {
                            allowed = rule.getString("action").equals("allow");
                        }
                    }
                }

                if (allowed) {
                    String osName;
                    switch(SystemUtil.getOSType()) {
                        case WINDOWS:
                            osName = "windows";
                            break;
                        case LINUX:
                            osName = "linux";
                            break;
                        case MAC:
                            osName = "osx";
                            break;
                        default:
                            osName = "";
                            break;
                    }

                    int osArch;
                    switch(SystemUtil.getArchitecture()) {
                        case X32:
                            osArch = 32;
                            break;
                        case X64:
                            osArch = 64;
                            break;
                        default:
                            osArch = -1;
                            break;
                    }

                    JSONObject natives = library.getJSONObject("natives");
                    if(!natives.has(osName)) {
                        continue;
                    }

                    String nativesType = natives.getString(osName).replace("${arch}", String.valueOf(osArch));
                    JSONObject classifiers = library.getJSONObject("downloads").getJSONObject("classifiers");
                    if(!classifiers.has(nativesType)) {
                        continue;
                    }

                    String url = classifiers.getJSONObject(nativesType).getString("url");
                    File downloadedJarFile = new File(versionNatives, library.getString("name").replace(":", ";") + ".jar");
                    FileUtils.copyURLToFile(new URL(url), downloadedJarFile);

                    JarFile jarFile = new JarFile(downloadedJarFile);
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while(entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if(!entry.getName().contains("/")) {
                            FileUtils.copyInputStreamToFile(jarFile.getInputStream(entry), new File(versionNatives, entry.getName()));
                        }
                    }
                }
            }
        }
    }

}
