package com.github.glassmc.kiln.standard.remapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.mapping.util.EntryTriple;

public class CSRGRemapper {

    public static HashRemapper create(File file) throws IOException {
        Map<String, String> classNames = new HashMap<>();
        Map<EntryTriple, String> fieldNames = new HashMap<>();
        Map<EntryTriple, String> methodNames = new HashMap<>();

        int lineNumber = 1;

        for(String line : Files.readAllLines(file.toPath())) {
            if(line.startsWith(".")) {
                continue;
            }

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
            } else {
                throw new MappingParseException(columns.length + " columns unsupported", lineNumber);
            }
        }

        return new HashRemapper(classNames, fieldNames, methodNames);
    }

}
