package com.github.glassmc.kiln.main;

import com.github.glassmc.kiln.common.Pair;
import com.github.glassmc.kiln.main.task.ClearMappings;
import com.github.glassmc.kiln.main.task.GenerateRunConfiguration;
import com.github.glassmc.kiln.standard.KilnStandardPlugin;
import com.github.glassmc.kiln.standard.mappings.IMappingsProvider;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class KilnMainPlugin implements Plugin<Project> {

    protected static KilnMainPlugin instance;

    public static KilnMainPlugin getInstance() {
        return instance;
    }

    protected Project project;

    private final List<Pair<IMappingsProvider, Boolean>> allMappingsProviders = new ArrayList<>();

    @Override
    public void apply(Project project) {
        instance = this;
        this.project = project;

        project.getPlugins().apply("kiln-standard");

        project.getTasks().register("genRunConfiguration", GenerateRunConfiguration.class);
        project.getTasks().register("clearMappings", ClearMappings.class);

        this.setupShadow();

        Task shadowJar = project.getTasks().findByName("shadowJar");
        if (shadowJar != null) {
            shadowJar.doLast(new FullBuiltJar());
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

    public void addAllMappingsProvider(IMappingsProvider mappingsProvider, boolean prefix) {
        this.allMappingsProviders.add(new Pair<>(mappingsProvider, prefix));
    }

    public List<Pair<IMappingsProvider, Boolean>> getAllMappingsProviders() {
        return allMappingsProviders;
    }

    public class FullBuiltJar implements Action<Task> {

        @Override
        public void execute(Task task) {
            Project project = task.getProject();
            File libs = new File(task.getProject().getBuildDir(), "libs");

            File allMapped = new File(libs + "/" + project.getName() + "-" + project.getVersion() + "-all-mapped.jar");
            if (!allMapped.exists()) {
                allMapped = new File(libs + "/" + project.getName() + "-all-mapped.jar");
            }

            List<Project> projects = new ArrayList<>();
            appendAllProjects(project, projects);

            List<File> filesToAppend = new ArrayList<>();
            for (Project project1 : projects) {
                File libs1 = new File(project1.getBuildDir(), "libs");
                File allMapped1 = new File(libs1 + "/" + project1.getName() + "-" + project1.getVersion() + "-all-mapped.jar");
                if (!allMapped1.exists()) {
                    allMapped1 = new File(libs1 + "/" + project1.getName() + "-all-mapped.jar");
                }

                filesToAppend.add(allMapped1);
            }

            List<String> alreadyAdded = new ArrayList<>();
            try {
                JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(new File(allMapped.getAbsolutePath().replace("-all-mapped.jar", "-all-all-mapped.jar")).toPath()));

                for (File file : filesToAppend) {
                    JarFile jarFile = new JarFile(file);
                    Enumeration<JarEntry> entries = jarFile.entries();

                    while (entries.hasMoreElements()) {
                        JarEntry jarEntry = entries.nextElement();

                        if (!alreadyAdded.contains(jarEntry.getName())) {
                            jarOutputStream.putNextEntry(new JarEntry(jarEntry.getName()));
                            jarOutputStream.write(IOUtils.toByteArray(jarFile.getInputStream(jarEntry)));
                            jarOutputStream.closeEntry();

                            alreadyAdded.add(jarEntry.getName());
                        }
                    }
                }

                jarOutputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public void appendAllProjects(Project parent, List<Project> projects) {
        if (parent.getBuildFile().exists() && !parent.getDisplayName().contains("launch")) {
            projects.add(parent);
        }

        for (Project project1 : parent.getChildProjects().values()) {
            appendAllProjects(project1, projects);
        }
    }

}
