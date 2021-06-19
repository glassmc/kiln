package io.github.glassmc.kiln.common;

import io.github.glassmc.sand.ClassMapping;
import io.github.glassmc.sand.Mapping;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class Util {

    public static File downloadMinecraft(String id, String version, File pluginCache) {
        File minecraftFile = new File(pluginCache, "minecraft");
        File versionFile = new File(minecraftFile, version);
        File versionJARFile = new File(versionFile, id + "-" + version + ".jar");
        File versionMappingsFile = new File(versionFile, id + ".mapping");
        File versionMappedJARFile = new File(versionFile, id + "-" + version + "-mapped.jar");

        if (!versionJARFile.exists()) {
            try {
                URL versionsURL = new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json");
                JSONObject versions = new JSONObject(IOUtils.toString(versionsURL, StandardCharsets.UTF_8));
                JSONObject versionInfo = new JSONObject();
                for(Object info : versions.getJSONArray("versions")) {
                    if(((JSONObject) info).getString("id").equals(version)) {
                        versionInfo = (JSONObject) info;
                    }
                }

                String versionManifestString = versionInfo.getString("url");
                URL versionManifestURL = new URL(versionManifestString);
                JSONObject versionManifest = new JSONObject(IOUtils.toString(versionManifestURL, StandardCharsets.UTF_8));

                if(id.equals("client")) {
                    File versionLibraries = new File(versionFile, "libraries");
                    File versionNatives = new File(versionFile, "natives");

                    downloadLibraries(versionManifest, versionLibraries);

                    downloadNatives(versionManifest, versionNatives);
                }

                URL versionJarURL = new URL(versionManifest.getJSONObject("downloads").getJSONObject(id).getString("url"));
                FileUtils.copyURLToFile(versionJarURL, versionJARFile);

                URL versionMappingsURL = new URL("https://raw.githubusercontent.com/glassmc/sand/main/mappings/" + id + ".mapping");
                FileUtils.copyURLToFile(versionMappingsURL, versionMappingsFile);

                Mapping mapping = Mapping.fromFile(versionMappingsFile);
                Remapper remapper = new Remapper() {
                    @Override
                    public String map(String internalName) {
                        ClassMapping classMapping = mapping.getClass("obfuscated", internalName);
                        return classMapping != null ? classMapping.getName("deobfuscated") : internalName;
                    }
                };
                JarOutputStream outputStream = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(versionMappedJARFile)));

                JarFile input = new JarFile(versionJARFile);
                Enumeration<JarEntry> entries = input.entries();
                while(entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if(!entry.isDirectory()) {
                        if(entry.getName().endsWith(".class") && !entry.getName().contains("/")) {
                            ClassReader classReader = new ClassReader(IOUtils.readFully(input.getInputStream(entry), (int) entry.getSize()));
                            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                            ClassVisitor visitor = new ClassRemapper(writer, remapper);
                            classReader.accept(visitor, ClassReader.EXPAND_FRAMES);

                            outputStream.putNextEntry(new JarEntry(remapper.map(entry.getName().replace(".class", "")) + ".class"));
                            outputStream.write(writer.toByteArray());
                        } else {
                            outputStream.putNextEntry(new JarEntry(entry.getName()));
                            outputStream.write(IOUtils.readFully(input.getInputStream(entry), (int) entry.getSize()));
                        }
                        outputStream.closeEntry();
                    }
                }
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return versionMappedJARFile;
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
                FileUtils.copyURLToFile(
                        new URL(artifactURL),
                        new File(versionLibraries, artifactURL.substring(artifactURL.lastIndexOf("/") + 1))
                );
            }
        }
    }

    private static void downloadNatives(JSONObject versionManifest, File versionNatives) throws IOException {
        for(Object element : versionManifest.getJSONArray("libraries")) {
            JSONObject library = (JSONObject) element;

            if(library.has("natives")) {
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
                File downloadedJarFile = new File(versionNatives, library.getString("name") + ".jar");
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
