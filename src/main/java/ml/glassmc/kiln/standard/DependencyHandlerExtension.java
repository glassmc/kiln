package ml.glassmc.kiln.standard;

import ml.glassmc.kiln.common.SystemUtil;
import ml.glassmc.kiln.common.Util;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.FileCollection;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

public class DependencyHandlerExtension {

    public static FileCollection minecraft(String id, String version) {
        KilnStandardPlugin plugin = KilnStandardPlugin.getInstance();
        File pluginCache = plugin.getCache();

        List<String> files = new ArrayList<>();

        File minecraftFile = new File(pluginCache, "minecraft");
        File versionFile = new File(minecraftFile, version);
        File versionJARFile = new File(versionFile, id + "-" + version + ".jar");
        File versionLibraries = new File(versionFile, "libraries");

        Util.downloadMinecraft(id, version, pluginCache);

        files.add(versionJARFile.getAbsolutePath());
        for (File file : Objects.requireNonNull(versionLibraries.listFiles())) {
            files.add(file.getAbsolutePath());
        }

        return plugin.getProject().files(files.toArray());
    }

    public static FileCollection shard(String id, String version) {
        KilnStandardPlugin plugin = KilnStandardPlugin.getInstance();

        File file = new File(plugin.getProject().getGradle().getGradleUserHomeDir() + "/caches/kiln/shard/" + id + "/" + version + "/" + id + "-" + version + ".jar");
        return plugin.getProject().files(file.getAbsoluteFile());
    }

}
