package com.github.glassmc.kiln.standard.mappings;

import com.github.glassmc.kiln.common.Pair;
import com.github.glassmc.kiln.standard.internalremapper.Remapper;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface IMappingsProvider {
    void setup(File minecraftFile, String version) throws NoSuchMappingsException;
    Remapper getRemapper(Direction direction);

    Map<String, Pair<Map<String, String>, List<String>>> getContext(Side side, boolean prefix);
    String getID();
    String getVersion();
    void clearCache(File minecraftFile);

    enum Direction {
        TO_NAMED,
        TO_OBFUSCATED
    }

    enum Side {
        NAMED,
        OBFUSCATED
    }
}
