package com.github.glassmc.kiln.main.task;

import com.github.glassmc.kiln.common.Util;
import com.github.glassmc.kiln.main.KilnMainPlugin;
import com.github.glassmc.kiln.standard.KilnStandardExtension;
import com.github.glassmc.kiln.standard.KilnStandardPlugin;
import com.github.glassmc.kiln.standard.environment.Environment;
import com.github.glassmc.kiln.standard.mappings.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

public abstract class ClearMappings extends DefaultTask {

    @TaskAction
    public void run() {
        this.setGroup("kiln");

        IMappingsProvider[] mappingsProviders = new IMappingsProvider[] { new MCPMappingsProvider(), new MojangMappingsProvider(), new YarnMappingsProvider() };

        File minecraftCache = new File(KilnMainPlugin.getInstance().getCache(), "minecraft");
        for (File folder : Objects.requireNonNull(minecraftCache.listFiles())) {
            for (File file : Objects.requireNonNull(folder.listFiles())) {
                if (file.getName().endsWith(".jar") && !file.getName().endsWith(folder.getName() + ".jar")) {
                    file.delete();
                }
            }

            for (IMappingsProvider mappingsProvider : mappingsProviders) {
                mappingsProvider.clearCache(folder);
            }
        }
    }

}
