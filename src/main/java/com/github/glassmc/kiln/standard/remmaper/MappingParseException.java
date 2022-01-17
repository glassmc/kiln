package com.github.glassmc.kiln.standard.remmaper;

public class MappingParseException extends RuntimeException {

    public MappingParseException(String message, int lineNumber) {
        super(message + " @ line " + lineNumber);
    }

}
