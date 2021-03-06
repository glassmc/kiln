package com.github.glassmc.kiln.environment;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class GlassLoaderEnvironment implements Environment {

    private final String version;

    public GlassLoaderEnvironment(String version) {
        this.version = version;
    }

    @Override
    public String getMainClass() {
        return "com.github.glassmc.loader.bootstrap.GlassMain";
    }

    @Override
    public String[] getProgramArguments(String environment, String version) {
        return new String[] { "--environment", environment, "--glassVersion", version };
    }

    @Override
    public String[] getRuntimeDependencies(File pluginCache) {
        File loader = new File(pluginCache, "loader");
        File loaderJar = new File(loader, "loader-" + version + ".jar");

        try {
            URL url = new URL("https://glassmc.ml/repository/com/github/glassmc/loader/" + version + "/loader-" + version + "-all.jar");
            FileUtils.copyURLToFile(url, loaderJar);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new String[] { loaderJar.getAbsolutePath() };
    }

    @Override
    public String getVersion(String version) {
        return "Glass-" + version;
    }

}
