package com.github.glassmc.kiln.main;

import com.github.glassmc.kiln.main.task.GenerateRunConfiguration;
import com.github.glassmc.kiln.main.task.GetRunConfiguration;
import com.github.glassmc.kiln.standard.KilnStandardPlugin;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication;

import java.io.File;

public class KilnMainPlugin implements Plugin<Project> {

    protected static KilnMainPlugin instance;

    public static KilnMainPlugin getInstance() {
        return instance;
    }

    protected Project project;

    @Override
    public void apply(Project project) {
        instance = this;
        this.project = project;

        project.getPlugins().apply("maven-publish");
        project.getPlugins().apply("kiln-standard");

        Provider<RegularFile> file = project.getLayout().getBuildDirectory().file("libs/" + project.getName() + "-mapped.jar");
        PublishArtifact artifact = project.getArtifacts().add("archives", file.get().getAsFile());

        PublishingExtension publishing = (PublishingExtension) project.getExtensions().getByName("publishing");

        publishing.getPublications().create("MavenPublication", MavenPublication.class, publication -> {
            publication.artifact(artifact);
        });

        for (Task task : project.getTasks()) {
            if (task.getName().startsWith("publish")) {
                task.dependsOn(project.getTasks().getByName("shadowJar"));
            }
        }

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

    public File getCache() {
        return KilnStandardPlugin.getInstance().getCache();
    }

}
