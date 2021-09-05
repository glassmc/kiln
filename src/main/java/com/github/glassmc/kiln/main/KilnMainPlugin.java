package com.github.glassmc.kiln.main;

import com.github.glassmc.kiln.main.task.GenerateRunConfiguration;
import com.github.glassmc.kiln.main.task.GetRunConfiguration;
import com.github.glassmc.kiln.standard.KilnStandardPlugin;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

public class KilnMainPlugin extends KilnStandardPlugin {

    public static KilnMainPlugin getInstance() {
        return (KilnMainPlugin) instance;
    }

    @Override
    public void apply(Project project) {
        super.apply(project);

        project.getTasks().register("getRunConfiguration", GetRunConfiguration.class);
        project.getTasks().register("genRunConfiguration", GenerateRunConfiguration.class);

        this.setupShadow();
    }

    private void setupShadow() {
        Configuration shadowRuntime = project.getConfigurations().create("shadowRuntime");
        Configuration runtimeOnly = project.getConfigurations().getByName("runtimeOnly");
        runtimeOnly.extendsFrom(shadowRuntime);

        ShadowJar shadowJar = (ShadowJar) project.getTasks().getByName("shadowJar");
        shadowJar.getConfigurations().add(project.getConfigurations().getByName("shadowRuntime"));
    }

}
