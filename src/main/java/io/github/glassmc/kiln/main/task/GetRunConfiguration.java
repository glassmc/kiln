package io.github.glassmc.kiln.main.task;

import io.github.glassmc.kiln.common.Util;
import io.github.glassmc.kiln.main.KilnMainPlugin;
import io.github.glassmc.kiln.standard.mappings.ObfuscatedMappingsProvider;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Objects;

public abstract class GetRunConfiguration extends DefaultTask {

    @TaskAction
    public void run() {
        String environment = (String) this.getProject().getProperties().get("environment");
        String version = (String) this.getProject().getProperties().get("version");

        File pluginCache = KilnMainPlugin.getInstance().getCache();
        File jar = Util.downloadMinecraft(environment, version, pluginCache, null);
        File dependencies = new File(jar.getParentFile(), "libraries");
        File natives = new File(jar.getParentFile(), "natives");

        System.out.print("-Xbootclasspath/a:" + jar.getAbsolutePath() + File.pathSeparator);
        for(File dependency : Objects.requireNonNull(dependencies.listFiles())) {
            System.out.print(dependency.getAbsolutePath() + File.pathSeparator);
        }
        System.out.println(" -Djava.library.path=" + natives.getAbsolutePath());
    }

}