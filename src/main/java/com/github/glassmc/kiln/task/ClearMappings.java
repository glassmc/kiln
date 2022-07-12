package com.github.glassmc.kiln.task;

import com.github.glassmc.kiln.KilnPlugin;
import com.github.glassmc.kiln.mappings.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Objects;

public abstract class ClearMappings extends DefaultTask {

    @TaskAction
    public void run() {
        this.setGroup("kiln");

        KilnPlugin kilnPlugin = this.getProject().getPlugins().getPlugin(KilnPlugin.class);
        /*IMappingsProvider[] mappingsProviders = new IMappingsProvider[] { new MCPMappingsProvider(), new MojangMappingsProvider(), new YarnMappingsProvider() };

        File minecraftCache = new File(KilnPlugin.getMainInstance().getCache(), "minecraft");
        for (File folder : Objects.requireNonNull(minecraftCache.listFiles())) {
            for (File file : Objects.requireNonNull(folder.listFiles())) {
                if (file.getName().endsWith(".jar") && !file.getName().endsWith(folder.getName() + ".jar")) {
                    file.delete();
                }
            }

            for (IMappingsProvider mappingsProvider : mappingsProviders) {
                mappingsProvider.clearCache(folder);
            }
        }*/
    }

}
