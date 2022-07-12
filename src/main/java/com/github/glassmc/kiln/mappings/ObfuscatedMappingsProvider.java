package com.github.glassmc.kiln.mappings;

import com.github.glassmc.kiln.Pair;
import com.github.glassmc.kiln.internalremapper.Remapper;

import java.io.File;
import java.util.List;
import java.util.Map;

public class ObfuscatedMappingsProvider implements IMappingsProvider {

    @Override
    public void setup(File minecraftFile, String version) {

    }

    @Override
    public Remapper getRemapper(Direction direction) {
        return new Remapper() { };
    }

    @Override
    public Map<String, Pair<Map<String, String>, List<String>>> getContext(Side side, boolean prefix) {
        return null;
    }

    @Override
    public String getID() {
        return "obfuscated";
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public void clearCache(File minecraftFile) {

    }

}
