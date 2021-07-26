package com.github.glassmc.kiln.main;

import com.github.glassmc.kiln.main.task.GenerateRunConfiguration;
import com.github.glassmc.kiln.main.task.GetRunConfiguration;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.ResolvableDependencies;

import java.io.File;
import java.util.*;

public class KilnMainPlugin implements Plugin<Project> {

    private static KilnMainPlugin instance;

    public static KilnMainPlugin getInstance() {
        return instance;
    }

    private Project project;

    @Override
    public void apply(Project project) {
        instance = this;
        this.project = project;

        project.getTasks().register("getRunConfiguration", GetRunConfiguration.class);
        project.getTasks().register("genRunConfiguration", GenerateRunConfiguration.class);

        Configuration shadowRuntime = project.getConfigurations().create("shadowRuntime");
        Configuration runtimeOnly = project.getConfigurations().getByName("runtimeOnly");
        runtimeOnly.extendsFrom(shadowRuntime);

        ShadowJar shadowJar = (ShadowJar) project.getTasks().getByName("shadowJar");
        shadowJar.getConfigurations().clear();
        shadowJar.getConfigurations().add(project.getConfigurations().getByName("shadowRuntime"));

        project.getGradle().addListener(new DependencyResolutionListener() {
            @Override
            public void beforeResolve(ResolvableDependencies resolvableDependencies) {
                project.getGradle().removeListener(this);
                for(Project project1 : collectAllProjects(project)) {
                    if(!project1.getBuildFile().exists() || project1.equals(project)) {
                        continue;
                    }
                    Map<String, Object> map = new HashMap<>();
                    map.put("path", project1.getPath());
                    map.put("configuration", "shadow");
                    shadowRuntime.getDependencies().add(project.getDependencies().create(project.getDependencies().project(map)));
                }
            }

            @Override
            public void afterResolve(ResolvableDependencies resolvableDependencies) {

            }
        });
    }

    List<Project> collectAllProjects(Project project) {
        List<Project> childProjects = new ArrayList<>();
        childProjects.add(project);

        for(Project childProject : project.getChildProjects().values()) {
            childProjects.addAll(collectAllProjects(childProject));
        }

        return childProjects;
    }

    String getName(Project project) {
        return project.getParent() != null ? getName(project.getParent()) + ':' + project.getName() : "";
    }

    public File getCache() {
        File cache = new File(this.project.getGradle().getGradleUserHomeDir() + "/caches/kiln");
        cache.mkdirs();
        return cache;
    }

    public Project getProject() {
        return project;
    }

}
