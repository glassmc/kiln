package com.github.glassmc.kiln.standard.environment;

import java.io.File;

public class CustomEnvironment implements Environment {

    private final String mainClass;

    public CustomEnvironment(String mainClass) {
        this.mainClass = mainClass;
    }

    @Override
    public String getMainClass() {
        return this.mainClass;
    }

    @Override
    public String[] getProgramArguments(String environment, String version) {
        return new String[0];
    }

    @Override
    public String[] getRuntimeDependencies(File pluginCache) {
        return new String[0];
    }

}
