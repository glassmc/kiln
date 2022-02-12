package com.github.glassmc.kiln.standard.mappings;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.github.glassmc.kiln.common.Pair;
import org.apache.commons.io.FileUtils;
import org.objectweb.asm.commons.Remapper;

import com.github.glassmc.kiln.standard.remapper.CSRGRemapper;
import com.github.glassmc.kiln.standard.remapper.CSVRemapper;
import com.github.glassmc.kiln.standard.remapper.HashRemapper;
import com.github.glassmc.kiln.standard.remapper.ReversibleRemapper;
import com.github.glassmc.kiln.standard.remapper.UniqueRemapper;

public class MCPMappingsProvider implements IMappingsProvider {

    private String version;
    // A map of the most stable MCP mappings for each version.
    private final Map<String, Pair<String, String>> mappings = new HashMap<String, Pair<String, String>>() {
        {
            put("1.7.10", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.7.10/mcp-1.7.10-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/12-1.7.10/mcp_stable-12-1.7.10.zip"
            ));
            put("1.8", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.8/mcp-1.8-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/18-1.8/mcp_stable-18-1.8.zip"
            ));
            put("1.8.8", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.8.8/mcp-1.8.8-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/20-1.8.8/mcp_stable-20-1.8.8.zip"
            ));
            put("1.8.9", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.8.9/mcp-1.8.9-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/22-1.8.9/mcp_stable-22-1.8.9.zip"
            ));
            put("1.9", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.9/mcp-1.9-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/24-1.9/mcp_stable-24-1.9.zip"
            ));
            put("1.9.2", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.9.2/mcp-1.9.2-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/24-1.9/mcp_stable-24-1.9.zip"
            ));
            put("1.9.4", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.9.4/mcp-1.9.4-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/26-1.9.4/mcp_stable-26-1.9.4.zip"
            ));
            put("1.10", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.10/mcp-1.10-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/29-1.10.2/mcp_stable-29-1.10.2.zip"
            ));
            put("1.10.2", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.10.2/mcp-1.10.2-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/29-1.10.2/mcp_stable-29-1.10.2.zip"
            ));
            put("1.11", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.11/mcp-1.11-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/32-1.11/mcp_stable-32-1.11.zip"
            ));
            put("1.11.1", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.11.1/mcp-1.11.1-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/32-1.11/mcp_stable-32-1.11.zip"
            ));
            put("1.11.2", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.11.2/mcp-1.11.2-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/32-1.11/mcp_stable-32-1.11.zip"
            ));
            put("1.12", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.12/mcp-1.12-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/39-1.12/mcp_stable-39-1.12.zip"
            ));
            put("1.12.1", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.12.1/mcp-1.12.1-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/39-1.12/mcp_stable-39-1.12.zip"
            ));
            put("1.12.2", Pair.of(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.12.2/mcp-1.12.2-csrg.zip",
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/39-1.12/mcp_stable-39-1.12.zip"
            ));
        }
    };

    private HashRemapper searge;
    private ReversibleRemapper reversedSearge;
    private UniqueRemapper named;
    private ReversibleRemapper reversedNamed;

    @Override
    public void setup(File minecraftFile, String version) throws NoSuchMappingsException {
        this.version = version;

        File temp = new File(minecraftFile, "temp");
        Pair<String, String> mappingURLs = mappings.get(version);

        if(mappingURLs == null) {
            throw new NoSuchMappingsException(version);
        }

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

}
