package com.github.glassmc.kiln.standard.mappings;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.github.glassmc.kiln.common.Pair;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.objectweb.asm.commons.Remapper;

import com.github.glassmc.kiln.standard.remapper.CSRGRemapper;
import com.github.glassmc.kiln.standard.remapper.CSVRemapper;
import com.github.glassmc.kiln.standard.remapper.HashRemapper;
import com.github.glassmc.kiln.standard.remapper.ReversibleRemapper;
import com.github.glassmc.kiln.standard.remapper.UniqueRemapper;

public class MCPMappingsProvider implements IMappingsProvider {

    private String version;

    private HashRemapper searge;
    private ReversibleRemapper reversedSearge;
    private UniqueRemapper named;
    private ReversibleRemapper reversedNamed;

    @Override
    public void setup(File minecraftFile, String version) throws NoSuchMappingsException {
        this.version = version;

        File mappingsJson = new File(minecraftFile.getParentFile().getParentFile(), "mappings.json");
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(FileUtils.readFileToString(mappingsJson, StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }

        JSONObject mappings = jsonObject.getJSONObject("mcp").getJSONObject(version);

        if (mappings == null) {
            throw new NoSuchMappingsException(version);
        }

        File temp = new File(minecraftFile, "temp");
        Pair<String, String> mappingURLs = new Pair<>(mappings.getString("srg"), mappings.getString("mcp"));

        URL seargeURL;
        URL namedURL;

        try {
            seargeURL = new URL(Objects.requireNonNull(mappingURLs.getLeft()));
            namedURL = new URL(Objects.requireNonNull(mappingURLs.getRight()));
        } catch(MalformedURLException e) {
            e.printStackTrace();
            return;
        }

        try {
            String seargeFileBase = seargeURL.getFile().substring(seargeURL.getFile().lastIndexOf("/")).substring(1)
                    .replace(".zip", "");
            String namedFileBase = namedURL.getFile().substring(namedURL.getFile().lastIndexOf("/")).substring(1)
                    .replace(".zip", "");

            File seargeMappings = new File(temp, seargeFileBase + "-joined.csrg");
            File fieldMappings = new File(temp, namedFileBase + "-fields.csv");
            File methodMappings = new File(temp, namedFileBase + "-methods.csv");

            if(!seargeMappings.exists() || !fieldMappings.exists()) {
                File seargeMappingsFile = new File(temp, seargeFileBase + ".zip");
                File namedMappingsFile = new File(temp, namedFileBase + ".zip");
                FileUtils.copyURLToFile(namedURL, namedMappingsFile);
                FileUtils.copyURLToFile(seargeURL, seargeMappingsFile);

                ZipFile seargeZIPFile = new ZipFile(seargeMappingsFile);
                FileUtils.copyInputStreamToFile(seargeZIPFile.getInputStream(new ZipEntry("joined.csrg")),
                        seargeMappings);
                seargeZIPFile.close();

                JarFile namedJARFile = new JarFile(namedMappingsFile);
                FileUtils.copyInputStreamToFile(namedJARFile.getInputStream(new ZipEntry("fields.csv")), fieldMappings);
                FileUtils.copyInputStreamToFile(namedJARFile.getInputStream(new ZipEntry("methods.csv")),
                        methodMappings);
                namedJARFile.close();
            }

            searge = CSRGRemapper.create(seargeMappings);
            reversedSearge = searge.reversed();
            named = CSVRemapper.create(null, fieldMappings, methodMappings, "searge", "name");
            reversedNamed = named.toNonUnique(searge).reversed();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Remapper getRemapper(Direction direction) {
        Remapper initial = direction == Direction.TO_NAMED ? searge : reversedNamed;
        Remapper result = direction == Direction.TO_NAMED ? named : reversedSearge;

        return new Remapper() {

            @Override
            public String map(String name) {
                return result.map(initial.map(name));
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                return result.mapMethodName(initial.map(owner), initial.mapMethodName(owner, name, descriptor),
                        initial.mapDesc(descriptor));
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                return result.mapFieldName(initial.map(owner), initial.mapFieldName(owner, name, descriptor), "");
            }

        };
    }

    @Override
    public String getID() {
        return "mcp";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public void clearCache(File minecraftFile) {
        File temp = new File(minecraftFile, "temp");
        try {
            if (temp.exists()) {
                FileUtils.cleanDirectory(temp);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
