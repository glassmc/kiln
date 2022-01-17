package com.github.glassmc.kiln.standard.mappings;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.json.JSONObject;
import org.objectweb.asm.commons.Remapper;

import com.github.glassmc.kiln.common.Util;
import com.github.glassmc.kiln.standard.remmaper.HashRemapper;
import com.github.glassmc.kiln.standard.remmaper.ProGuardRemapper;

import shadow.com.gradle.publish.plugin.dep.org.apache.commons.io.FileUtils;

public class MojangMappingsProvider implements IMappingsProvider {

    private String version;
    private HashRemapper deobfuscator;
    private HashRemapper obfuscator;

    @Override
    public void setup(File minecraftFile, String version) throws NoSuchMappingsException {
        try {
            this.version = version;

            File temp = new File(minecraftFile, "temp");

            JSONObject versionManifest = Util.getVersionManifest(version);

            JSONObject downloads = versionManifest.getJSONObject("downloads");

            // Use client-sided mappings as they contain both sides.
            if(!downloads.has("client_mappings")) {
                throw new NoSuchMappingsException(version);
            }

            URL mappingsURL = new URL(downloads.getJSONObject("client_mappings").getString("url"));

            File mappingsFile = new File(temp, "mappings.proguard.txt");
            FileUtils.copyURLToFile(mappingsURL, mappingsFile);

            obfuscator = ProGuardRemapper.create(mappingsFile);
            deobfuscator = obfuscator.reversed();
        } catch(IOException error) {
            error.printStackTrace();
        }
    }

    @Override
    public Remapper getRemapper(Direction direction) {
        if(direction == Direction.TO_OBFUSCATED) {
            return obfuscator;
        }

        return deobfuscator;
    }

    @Override
    public String getID() {
        return "mojang";
    }

    @Override
    public String getVersion() {
        return version;
    }

}
