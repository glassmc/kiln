package com.github.glassmc.kiln.main;

import com.github.glassmc.kiln.main.task.ClearMappings;
import com.github.glassmc.kiln.main.task.GenerateRunConfiguration;
import com.github.glassmc.kiln.standard.KilnStandardPlugin;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

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

        //project.getPlugins().apply("maven-publish");
        project.getPlugins().apply("kiln-standard");

        //PublishingExtension publishing = (PublishingExtension) project.getExtensions().getByName("publishing");

        /*for (Task task : project.getTasks()) {
            if (task.getName().startsWith("publish")) {
                task.dependsOn(project.getTasks().getByName("shadowJar"));
                task.dependsOn(project.getTasks().getByName("build"));
            }
        }*/

        project.getTasks().register("genRunConfiguration", GenerateRunConfiguration.class);
        project.getTasks().register("clearMappings", ClearMappings.class);

        //this.setupShadow();

        this.appendProject(project, project);

        //project.afterEvaluate(project1 -> publishing.getPublications().create("MavenPublication", MavenPublication.class, publication -> publication.from(project.getComponents().getByName("java"))));
    }

    private void appendProject(Project mainProject, Project project) {
        if (mainProject != project && project.getBuildFile().exists()) {
            String displayName = project.getDisplayName();
            mainProject.getDependencies().add("runtimeOnly", mainProject.project(displayName.substring(displayName.indexOf("'") + 1, displayName.lastIndexOf("'"))));
            //mainProject.getDependencies().add("shadowApi", mainProject.files(new File(project.getBuildDir(), "libs/" + project.getName() + "-" + project.getVersion() + "-all.jar").getAbsolutePath()));
            //mainProject.getDependencies().add("shadowApi", mainProject.files(new File(project.getBuildDir(), "libs/" + project.getName() + "-all.jar").getAbsolutePath()));
        }

        for (Project subProject : project.getChildProjects().values()) {
            this.appendProject(mainProject, subProject);
        }
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
