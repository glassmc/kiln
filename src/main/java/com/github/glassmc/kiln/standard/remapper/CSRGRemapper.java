package com.github.glassmc.kiln.standard.remapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import net.fabricmc.mapping.util.EntryTriple;

public class CSRGRemapper {

    public static HashRemapper create(File seargeMappingsFile, File seargeMappingsParamsFile) throws IOException {
        Map<String, String> classNames = new HashMap<>();
        Map<EntryTriple, String> fieldNames = new HashMap<>();
        Map<EntryTriple, String> methodNames = new HashMap<>();
        Map<EntryTriple, Map<Integer, String>> parameterNames = new HashMap<>();

        Map<String, EntryTriple> reversedMethodNames = new HashMap<>();

        int lineNumber = 1;

        for(String line : Files.readAllLines(seargeMappingsFile.toPath())) {
            if(line.startsWith(".")) continue;

            String[] columns = line.split(" ");

            if(columns.length == 2) {
                String srcName = columns[0];
                String dstName = columns[1];

                if(srcName.endsWith("/")) {
                    continue; // Skip packages.
                }

                classNames.put(srcName, dstName);
            } else if(columns.length == 3) {
                String srcOwner = columns[0];
                String srcName = columns[1];
                String dstName = columns[2];

                fieldNames.put(new EntryTriple(srcOwner, srcName, ""), dstName);
            } else if(columns.length == 4) {
                String srcOwner = columns[0];
                String srcName = columns[1];
                String srcDesc = columns[2];
                String dstName = columns[3];

                methodNames.put(new EntryTriple(srcOwner, srcName, srcDesc), dstName);
                reversedMethodNames.put(dstName, new EntryTriple(srcOwner, srcName, srcDesc));
            } else {
                throw new MappingParseException(columns.length + " columns unsupported", lineNumber);
            }
        }

        HashRemapper hashRemapper = new HashRemapper(classNames, fieldNames, methodNames, new HashMap<>()).reversed();

        for (String line : Files.readAllLines(seargeMappingsParamsFile.toPath())) {
            if (!line.contains("|")) continue;
            String[] split = line.split("\\|");
            if (split.length == 1) continue;

            List<String> params = Arrays.asList(split[1].split(","));

            String className = line.substring(0, line.indexOf("."));

            Map<Integer, String> parameters = new HashMap<>();
            for (String parameter : params) {
                parameters.put(params.indexOf(parameter) + 1, parameter);
            }

            parameterNames.put(new EntryTriple(hashRemapper.map(className), "<init>", hashRemapper.mapDesc(line.substring(line.indexOf("("), line.indexOf("=")))), parameters);
        }

        return new HashRemapper(classNames, fieldNames, methodNames, parameterNames);
    }

}
