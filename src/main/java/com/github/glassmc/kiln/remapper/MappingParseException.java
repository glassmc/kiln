package com.github.glassmc.kiln.remapper;

public class MappingParseException extends RuntimeException {

    public MappingParseException(String message, int lineNumber) {
        super(message + " @ line " + lineNumber);
    }

}
