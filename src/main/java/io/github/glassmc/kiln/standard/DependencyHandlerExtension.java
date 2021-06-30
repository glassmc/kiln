package io.github.glassmc.kiln.standard;

import io.github.glassmc.kiln.common.Util;
import io.github.glassmc.kiln.standard.mappings.IMappingsProvider;
import io.github.glassmc.kiln.standard.mappings.ObfuscatedMappingsProvider;
import io.github.glassmc.kiln.standard.mappings.YarnMappingsProvider;
import org.gradle.api.file.FileCollection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DependencyHandlerExtension {

    public static IMappingsProvider mappingsProvider;

    public static FileCollection minecraft(String id, String version, String mappingsProviderId) {
        KilnStandardPlugin plugin = KilnStandardPlugin.getInstance();
        File pluginCache = plugin.getCache();

        switch(mappingsProviderId) {
            case "yarn":
                mappingsProvider = new YarnMappingsProvider();
                break;
            case "obfuscated":
            default:
                mappingsProvider = new ObfuscatedMappingsProvider();
        }

        List<String> files = new ArrayList<>();

        File minecraftFile = new File(pluginCache, "minecraft");
        File versionFile = new File(minecraftFile, version);
        File versionJARFile = new File(versionFile, id + "-" + version + ".jar");
        File versionMappedJARFile = new File(versionFile, id + "-" + version + "-" + mappingsProvider.getID() + ".jar");
        File versionLibraries = new File(versionFile, "libraries");

        mappingsProvider.setup(versionFile, version);

        Util.downloadMinecraft(id, version, pluginCache, mappingsProvider);

        files.add(versionMappedJARFile.getAbsolutePath());
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
