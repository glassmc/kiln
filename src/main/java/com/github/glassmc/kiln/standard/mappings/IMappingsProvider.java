package com.github.glassmc.kiln.standard.mappings;

import com.github.glassmc.kiln.standard.internalremapper.Remapper;

import java.io.File;

public interface IMappingsProvider {
    void setup(File minecraftFile, String version) throws NoSuchMappingsException;
    Remapper getRemapper(Direction direction);
    String getID();
    String getVersion();
    void clearCache(File minecraftFile);

    enum Direction {
        TO_NAMED,
        TO_OBFUSCATED
    }
}
