package ml.glassmc.kiln.standard;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;

public class KilnStandardPlugin implements Plugin<Project> {

    private static KilnStandardPlugin instance;

    public static KilnStandardPlugin getInstance() {
        return instance;
    }

    private Project project;

    @Override
    public void apply(Project project) {
        instance = this;
        this.project = project;
    }

    public File getCache() {
        File cache = new File(this.project.getGradle().getGradleUserHomeDir() + "/caches/glass");
        cache.mkdirs();
        return cache;
    }

    public File getLocalRepository() {
        return new File(this.getCache(), "repository");
    }

    public Project getProject() {
        return project;
    }

}
