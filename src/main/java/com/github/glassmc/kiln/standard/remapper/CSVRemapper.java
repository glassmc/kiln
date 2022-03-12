package com.github.glassmc.kiln.standard.remapper;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

public class CSVRemapper {

    public static UniqueRemapper create(File classesFile, File fieldsFile, File methodsFile, File paramsFile, String srcColumn, String paramSrcColumn, String dstColumn) throws IOException {
        Map<String, String> classNames = new HashMap<>();
        Map<String, String> fieldNames = new HashMap<>();
        Map<String, String> methodNames = new HashMap<>();
        Map<String, String> paramNames = new HashMap<>();

        if(classesFile != null) {
            classNames = toMap(classesFile, srcColumn, dstColumn);
        }
        if(fieldsFile != null) {
            fieldNames = toMap(fieldsFile, srcColumn, dstColumn);
        }
        if(methodsFile != null) {
            methodNames = toMap(methodsFile, srcColumn, dstColumn);
        }
        if(paramsFile != null) {
            paramNames = toMap(paramsFile, paramSrcColumn, dstColumn);
        }

        return new UniqueRemapper(classNames, fieldNames, methodNames, paramNames);
    }

    private static Map<String, String> toMap(File file, String srcColumn, String dstColumn) throws IOException {
        Map<String, String> result = new HashMap<>();

        FileReader reader = new FileReader(file);

        Iterable<CSVRecord> records = CSVFormat.Builder.create().setHeader().build().parse(reader);

        for(CSVRecord record : records) {
            result.put(record.get(srcColumn), record.get(dstColumn));
        }

        return result;
    }

}
