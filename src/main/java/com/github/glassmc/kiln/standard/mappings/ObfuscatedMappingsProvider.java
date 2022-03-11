package com.github.glassmc.kiln.standard.mappings;

import org.objectweb.asm.commons.Remapper;

import java.io.File;

public class ObfuscatedMappingsProvider implements IMappingsProvider {

    @Override
    public void setup(File minecraftFile, String version) {

    }

    @Override
    public Remapper getRemapper(Direction direction) {
        return new Remapper() { };
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
