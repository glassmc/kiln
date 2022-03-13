package com.github.glassmc.kiln.standard;

import com.github.glassmc.kiln.standard.environment.Environment;

import java.util.ArrayList;
import java.util.List;

public abstract class KilnStandardExtension {

    public List<CustomTransformer> transformers = new ArrayList<>();
    public Environment environment = null;
    public List<String> minecraft = new ArrayList<>();

}
