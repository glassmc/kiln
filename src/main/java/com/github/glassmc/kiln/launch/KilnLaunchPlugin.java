package com.github.glassmc.kiln.launch;

import com.github.glassmc.kiln.KilnPlugin;
import com.github.glassmc.kiln.Pair;
import com.github.glassmc.kiln.Util;
import com.github.glassmc.kiln.mappings.IMappingsProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class KilnLaunchPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPlugins().apply("java");
        KilnLaunchExtension extension = project.getExtensions().create("kiln_launch", KilnLaunchExtension.class);

        for (Project project1 : getAllProjects(project.getRootProject())) {
            if (!project1.getPath().contains("launch") && project1.getBuildFile().exists()) {
                project.evaluationDependsOn(project1.getPath());
            }
        }

        project.afterEvaluate(project1 -> {
            File pluginCache = KilnPlugin.getMainInstance().getCache();
            File versionFile = new File(pluginCache, "minecraft/" + extension.version);
            File dependencies = new File(versionFile, "libraries");

            try {
                for (Project project2 : getAllProjects(project1.getRootProject())) {
                    if (!project2.getPath().contains("launch") && project2.getBuildFile().exists()) {
                        for (ArtifactRepository repository : project2.getRepositories()) {
                            if (repository instanceof MavenArtifactRepository) {
                                MavenArtifactRepository mavenRepository = (MavenArtifactRepository) repository;
                                project1.getRepositories().maven(mavenArtifactRepository -> {
                                    mavenArtifactRepository.setUrl(mavenRepository.getUrl());
                                });
                            }
                        }
                    }
                }

                for (Project project2 : getAllProjects(project1.getRootProject())) {
                    if (!project2.getPath().contains("launch") && project2.getBuildFile().exists()) {
                        project.getDependencies().add("implementation", project.project(project2.getPath()));
                    }
                }

                JSONObject versionManifest = Util.getVersionManifest(extension.version);

                Map<String, String> libraries = new HashMap<>();

                for (Object library : versionManifest.getJSONArray("libraries")) {
                    JSONObject libraryJSON = (JSONObject) library;
                    JSONObject downloads = libraryJSON.getJSONObject("downloads");
                    if (downloads.has("artifact")) {
                        String url = downloads.getJSONObject("artifact").getString("url");
                        url = url.substring(url.lastIndexOf("/") + 1);

                        String id = libraryJSON.getString("name");

                        libraries.put(url, id);
                    }
                }

                File minecraftFile = new File(pluginCache, "minecraft");
                File localMaven = new File(minecraftFile, "localMaven");

                IMappingsProvider mappingsProvider = null;
                for (Pair<IMappingsProvider, Boolean> mappingsProviderPair : KilnPlugin.getMainInstance().getAllMappingsProviders()) {
                    if (mappingsProviderPair.getLeft().getVersion().equals(extension.version)) {
                        mappingsProvider = mappingsProviderPair.getLeft();
                    }
                }

                File versionMappedJARFile = new File(localMaven, "net/minecraft/" + extension.environment + "-" + extension.version + "/" + mappingsProvider.getID() + "-" + mappingsProvider.getMappingsVersion() + "/" + extension.environment + "-" + extension.version + "-" + mappingsProvider.getID() + "-" + mappingsProvider.getMappingsVersion() + ".jar");
                project.getDependencies().add("runtimeOnly", project.files(versionMappedJARFile.getAbsolutePath()));

                for(File dependency : Objects.requireNonNull(dependencies.listFiles())) {
                    if (!dependency.getName().endsWith(".jar")) continue;
                    String library = libraries.get(dependency.getName());
                    String[] id = library.split(":");
                    File file = new File(localMaven, id[0].replace(".", "/") + "/" + id[1] + "-" + extension.version + "/" + id[2] + "/" + id[1] + "-" + extension.version + "-" + id[2] + ".jar");

                    project.getDependencies().add("runtimeOnly", project.files(file.getAbsolutePath()));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private List<Project> getAllProjects(Project project) {
        List<Project> projects = new ArrayList<>();
        projects.add(project);

        for (Project project1 : project.getChildProjects().values()) {
            projects.addAll(this.getAllProjects(project1));
        }

        return projects;
    }

}
