package com.github.glassmc.kiln.standard.remapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.mapping.util.EntryTriple;

public class ProGuardRemapper {

    public static HashRemapper create(File file) throws IOException {
        Map<String, String> classNames = new HashMap<>();
        Map<EntryTriple, String> fieldNames = new HashMap<>();
        Map<EntryTriple, String> methodNames = new HashMap<>();

        String currentClass = null;
        int lineNumber = 1;

        for(String line : Files.readAllLines(file.toPath())) {
            line = removeIndentation(line);

            if(line.startsWith("#") || line.isEmpty()) {
                continue;
            }

            int arrowIndex = line.indexOf("->");

            if(arrowIndex == -1) {
                throw new MappingParseException("Invalid entry: must contain arrow (->)", lineNumber);
            }

            if(line.endsWith(":")) {
                currentClass = line.substring(0, arrowIndex - 1).replace(".", "/");
                classNames.put(currentClass, line.substring(arrowIndex + 3, line.indexOf(":")));
            } else {
                try {
                    int lastColonIndex = line.lastIndexOf(':');
                    line = line.substring(lastColonIndex + 1);

                    String returnType = line.substring(0, line.indexOf(' '));
                    line = line.substring(line.indexOf(' ') + 1);

                    boolean method = true;

                    int nameTo = line.indexOf('(');
                    if(nameTo == -1) {
                        method = false;
                        nameTo = line.indexOf(' ');
                    }

                    String name = line.substring(0, nameTo);

                    if(method) {
                        line = line.substring(nameTo);
                    }

                    String arguments = null;

                    if(method) {
                        arguments = line.substring(0, line.indexOf(' '));
                    }

                    line = line.substring(line.indexOf('>') + 2);

                    if(method) {
                        methodNames.put(new EntryTriple(currentClass, name,
                                toDescriptor(arguments.substring(1, arguments.length() - 1).split(","), returnType)),
                                line);
                    } else {
                        fieldNames.put(new EntryTriple(currentClass, name, ""), line);
                    }
                } catch(IndexOutOfBoundsException error) {
                    throw new MappingParseException("Invalid member mapping", lineNumber);
                }
            }

            lineNumber++;
        }

        return new HashRemapper(classNames, fieldNames, methodNames);
    }

    private static String toDescriptor(String type) {
        if(type.isEmpty()) {
            return type;
        }

        int dimensions = 0;
        while(type.contains("[]")) {
            dimensions++;
            type = type.replace("[]", "");
        }

        StringBuilder descriptor = new StringBuilder();

        for(int i = 0; i < dimensions; i++) {
            descriptor.append("[");
        }

        switch(type) {
        case "byte":
            descriptor.append("B");
            break;
        case "char":
            descriptor.append("C");
            break;
        case "double":
            descriptor.append("D");
            break;
        case "float":
            descriptor.append("F");
            break;
        case "int":
            descriptor.append("I");
            break;
        case "long":
            descriptor.append("J");
            break;
        case "short":
            descriptor.append("S");
            break;
        case "boolean":
            descriptor.append("Z");
            break;
        case "void":
            descriptor.append("V");
            break;
        default:
            descriptor.append("L").append(type.replace(".", "/")).append(";");
            break;
        }

        return descriptor.toString();
    }

    private static String toDescriptor(String[] args, String type) {
        StringBuilder result = new StringBuilder();

        result.append("(");

        for(String arg : args) {
            result.append(toDescriptor(arg));
        }

        result.append(")");

        result.append(toDescriptor(type));

        return result.toString();
    }

    private static String removeIndentation(String line) {
        while(isIndented(line)) {
            line = line.substring(1);
        }

        return line;
    }

    private static boolean isIndented(String line) {
        return Character.isWhitespace(line.charAt(0));
    }

}
