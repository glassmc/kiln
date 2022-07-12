package com.github.glassmc.kiln.environment;

import java.io.File;

public interface Environment {
    String getMainClass();
    String[] getProgramArguments(String environment, String version);
    String[] getRuntimeDependencies(File pluginCache);
    String getVersion(String version);
}
