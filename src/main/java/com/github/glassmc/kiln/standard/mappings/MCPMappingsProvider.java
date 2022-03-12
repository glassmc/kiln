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
import com.github.glassmc.kiln.standard.internalremapper.Remapper;

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
            String seargeFileBase = seargeURL.getFile().substring(seargeURL.getFile().lastIndexOf("/")).substring(1).replace(".zip", "");
            String namedFileBase = namedURL.getFile().substring(namedURL.getFile().lastIndexOf("/")).substring(1).replace(".zip", "");

            File seargeMappings = new File(temp, seargeFileBase + "-joined.csrg");
            File seargeMappingsParams = new File(temp, seargeFileBase + "-joined.exc");
            File staticMethods = new File(temp, seargeFileBase + "-static_methods.txt");
            File fieldMappings = new File(temp, namedFileBase + "-fields.csv");
            File methodMappings = new File(temp, namedFileBase + "-methods.csv");
            File paramMappings = new File(temp, namedFileBase + "-params.csv");

            if(!seargeMappings.exists() || !fieldMappings.exists()) {
                File seargeMappingsFile = new File(temp, seargeFileBase + ".zip");
                File namedMappingsFile = new File(temp, namedFileBase + ".zip");
                FileUtils.copyURLToFile(namedURL, namedMappingsFile);
                FileUtils.copyURLToFile(seargeURL, seargeMappingsFile);

                ZipFile seargeZIPFile = new ZipFile(seargeMappingsFile);
                FileUtils.copyInputStreamToFile(seargeZIPFile.getInputStream(new ZipEntry("joined.csrg")), seargeMappings);
                FileUtils.copyInputStreamToFile(seargeZIPFile.getInputStream(new ZipEntry("joined.exc")), seargeMappingsParams);
                FileUtils.copyInputStreamToFile(seargeZIPFile.getInputStream(new ZipEntry("static_methods.txt")), staticMethods);
                seargeZIPFile.close();

                JarFile namedJARFile = new JarFile(namedMappingsFile);
                FileUtils.copyInputStreamToFile(namedJARFile.getInputStream(new ZipEntry("fields.csv")), fieldMappings);
                FileUtils.copyInputStreamToFile(namedJARFile.getInputStream(new ZipEntry("methods.csv")), methodMappings);
                FileUtils.copyInputStreamToFile(namedJARFile.getInputStream(new ZipEntry("params.csv")), paramMappings);
                namedJARFile.close();
            }

            searge = CSRGRemapper.create(seargeMappings, seargeMappingsParams);
            reversedSearge = searge.reversed();
            named = CSVRemapper.create(null, fieldMappings, methodMappings, paramMappings, "searge", "param", "name");
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

            @Override
            public String mapVariableName(String clazz, String method, String methodDesc, String name, int index) {
                String newName = initial.mapVariableName(clazz, method, methodDesc, name, index);
                String methodName = initial.mapMethodName(clazz, method, methodDesc);

                if (newName.equals(name) && !name.equals("this") && methodName.startsWith("func_")) {
                    newName = "p_" + methodName.substring(methodName.indexOf("_") + 1, methodName.lastIndexOf("_")) + "_" + index + "_";
                }

                return result.mapVariableName(initial.map(clazz), methodName, initial.mapMethodDesc(methodDesc), newName, index);
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
