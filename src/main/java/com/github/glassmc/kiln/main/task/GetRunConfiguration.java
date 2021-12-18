package com.github.glassmc.kiln.main.task;

import com.github.glassmc.kiln.main.KilnMainPlugin;
import com.github.glassmc.kiln.standard.mappings.ObfuscatedMappingsProvider;
import com.github.glassmc.kiln.common.Util;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Objects;

public abstract class GetRunConfiguration extends DefaultTask {

    @TaskAction
    public void run() {
        String arguments = (String) this.getProject().getProperties().get("configuration");
        String[] argumentsSplit = arguments.split(",");

        String environment = argumentsSplit[0];
        String version = argumentsSplit[1];

        File pluginCache = KilnMainPlugin.getInstance().getCache();
        File jar = Util.setupMinecraft(environment, version, pluginCache, new ObfuscatedMappingsProvider());
        File dependencies = new File(jar.getParentFile(), "libraries");
        File natives = new File(jar.getParentFile(), "natives");

        System.out.print("-Xbootclasspath/a:" + jar.getAbsolutePath() + File.pathSeparator);
        for(File dependency : Objects.requireNonNull(dependencies.listFiles())) {
            System.out.print(dependency.getAbsolutePath() + File.pathSeparator);
        }
        System.out.println(" -Djava.library.path=" + natives.getAbsolutePath());
    }

}
