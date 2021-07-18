package com.github.glassmc.kiln.standard;

import com.github.glassmc.kiln.standard.mappings.IMappingsProvider;
import com.github.glassmc.kiln.common.Util;
import com.github.glassmc.kiln.standard.mappings.NoSuchMappingsException;
import com.github.glassmc.kiln.standard.mappings.ObfuscatedMappingsProvider;
import com.github.glassmc.kiln.standard.mappings.YarnMappingsProvider;
import com.github.jezza.Toml;
import com.github.jezza.TomlTable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.FileCollection;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@SuppressWarnings("unused")
public class DependencyHandlerExtension {

    public static FileCollection minecraft(String id, String version, String mappingsProviderId) {
        KilnStandardPlugin plugin = KilnStandardPlugin.getInstance();
        File pluginCache = plugin.getCache();

        IMappingsProvider mappingsProvider;
        switch(mappingsProviderId) {
            case "yarn":
                mappingsProvider = new YarnMappingsProvider();
                break;
            case "obfuscated":
            default:
                mappingsProvider = new ObfuscatedMappingsProvider();
        }
        plugin.setMappingsProvider(mappingsProvider);

        List<String> files = new ArrayList<>();

        File minecraftFile = new File(pluginCache, "minecraft");
        File versionFile = new File(minecraftFile, version);
        File versionMappedJARFile = new File(versionFile, id + "-" + version + "-" + mappingsProvider.getID() + ".jar");
        File versionLibraries = new File(versionFile, "libraries");

        Util.downloadMinecraft(id, version, pluginCache, new ObfuscatedMappingsProvider());

        try {
            mappingsProvider.setup(versionFile, version);
        } catch (NoSuchMappingsException e) {
            e.printStackTrace();
        }

        Util.downloadMinecraft(id, version, pluginCache, mappingsProvider);

        mappingsProvider.destroy();

        files.add(versionMappedJARFile.getAbsolutePath());
        for (File file : Objects.requireNonNull(versionLibraries.listFiles())) {
            files.add(file.getAbsolutePath());
        }

        return plugin.getProject().files(files.toArray());
    }

    public static FileCollection shard(String id, String version) {
        KilnStandardPlugin plugin = KilnStandardPlugin.getInstance();

        File file = new File(plugin.getProject().getGradle().getGradleUserHomeDir() + "/caches/kiln/shard/" + id + "-" + version + ".jar");
        file.getParentFile().mkdirs();

        FileCollection files = plugin.getProject().files(file);

        try {
            URL url = new URL("https://raw.githubusercontent.com/glassmc/registry/main/shards/" + id + "/" + version + "/" + id + "-" + version + ".toml");
            String shardVersionData = IOUtils.toString(url, StandardCharsets.UTF_8);
            TomlTable shardVersionDataTOML = Toml.from(new StringReader(shardVersionData));
            if(!file.exists()) {
                String shardFile = (String) shardVersionDataTOML.get("file");
                if(shardFile != null) {
                    FileUtils.copyURLToFile(new URL(shardFile), file);
                }
            }

            TomlTable downloadsToml = (TomlTable) shardVersionDataTOML.getOrDefault("downloads", new TomlTable());
            for(String downloadID : downloadsToml.keySet()) {
                files = files.plus(shard(downloadID, (String) downloadsToml.get(downloadID)));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return files;
    }

}
