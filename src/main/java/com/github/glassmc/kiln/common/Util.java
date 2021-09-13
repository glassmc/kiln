package com.github.glassmc.kiln.common;

import com.github.glassmc.kiln.standard.mappings.IMappingsProvider;
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

    public static File setupMinecraft(String id, String version, File pluginCache, IMappingsProvider mappingsProvider) {
        File minecraftFile = new File(pluginCache, "minecraft");
        File versionFile = new File(minecraftFile, version);
        File versionJARFile = new File(versionFile, id + "-" + version + ".jar");
        File versionMappedJARFile = new File(versionFile, id + "-" + version + "-" + mappingsProvider.getID() + ".jar");

        if (!versionMappedJARFile.exists()) {
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
                JarFile input = new JarFile(versionJARFile);

                Remapper remapper = mappingsProvider.getRemapper(IMappingsProvider.Direction.TO_NAMED);

                Remapper remapperWrapper = new Remapper() {

                    @Override
                    public String map(String name) {
                        String mapped = remapper.map(name);
                        if (input.getJarEntry(name + ".class") != null && mappingsProvider.getVersion() != null) {
                            return "v" + version.replace(".", "_") + "/" + mapped;
                        } else {
                            return name;
                        }
                    }

                    @Override
                    public String mapFieldName(String owner, String name, String descriptor) {
                        return remapper.mapFieldName(owner, name, descriptor);
                    }

                    @Override
                    public String mapMethodName(String owner, String name, String descriptor) {
                        return remapper.mapMethodName(owner, name, descriptor);
                    }

                };

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
