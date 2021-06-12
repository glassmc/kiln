package ml.glassmc.kiln.standard;

import ml.glassmc.kiln.common.SystemUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.FileCollection;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DependencyHandlerExtension {

    private static final String loaderVersion = "2c0cc57cfb";

    public static FileCollection shard(String id) {
        return shard(id, "none");
    }

    public static FileCollection shard(String id, String version) {
        KilnStandardPlugin plugin = KilnStandardPlugin.getInstance();

        FileCollection virtualDependency = handleVirtualDependency(plugin, id, version);
        if(virtualDependency != null) {
            return virtualDependency;
        }

        File file = new File("${plugin.project.gradle.gradleUserHomeDir}/caches/kiln/shard/$id/$version/$id-$version.jar");
        return plugin.getProject().files(file.getAbsoluteFile());
    }

    private static FileCollection handleVirtualDependency(KilnStandardPlugin plugin, String id, String version) {
        File pluginCache = plugin.getCache();
        switch (id) {
            case "loader-client": {
                File clientLoaderFile = new File(pluginCache, "loader/loader-client.jar");
                if (!clientLoaderFile.exists()) {
                    try {
                        URL clientLoaderURL = new URL("https://jitpack.io/com/github/glassmc/loader/client/$loaderVersion/client-$loaderVersion.jar");
                        FileUtils.copyURLToFile(clientLoaderURL, clientLoaderFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return plugin.getProject().files(clientLoaderFile.getAbsolutePath());
            }
            case "client": {
                List<String> files = new ArrayList<>();

                File minecraftFile = new File(pluginCache, "minecraft");
                File versionFile = new File(minecraftFile, version);
                File versionJARFile = new File(versionFile, "$id-$version.jar");
                File versionLibraries = new File(versionFile, "libraries");

                if (!versionJARFile.exists()) {
                    try {
                        InputStream versionsInputStream = new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json").openStream();
                        JSONObject versions = new JSONObject(new String(IOUtils.readFully(versionsInputStream, versionsInputStream.available())));
                        JSONObject versionInfo = new JSONObject();
                        for(Object info : versions.getJSONArray("versions")) {
                            if(((JSONObject) info).getString("id").equals(version)) {
                                versionInfo = (JSONObject) info;
                            }
                        }

                        String url = versionInfo.getString("url");
                        InputStream inputStream = new URL(url).openStream();
                        JSONObject versionManifest = new JSONObject(new String(IOUtils.readFully(inputStream, inputStream.available())));

                        URL versionJarURL = new URL(versionManifest.getJSONObject("downloads").getJSONObject("client").getString("url"));
                        FileUtils.copyURLToFile(versionJarURL, versionJARFile);

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
                                        new File(versionLibraries, url.substring(url.lastIndexOf("/") + 1))
                                );
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                files.add(versionJARFile.getAbsolutePath());
                for (File file : Objects.requireNonNull(versionLibraries.listFiles())) {
                    files.add(file.getAbsolutePath());
                }

                return plugin.getProject().files(files.toArray());
            }
            default: {
                return null;
            }
        }
    }

}
