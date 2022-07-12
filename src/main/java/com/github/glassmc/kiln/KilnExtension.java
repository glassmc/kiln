package com.github.glassmc.kiln;

import com.github.glassmc.kiln.environment.Environment;

import java.util.ArrayList;
import java.util.List;

public abstract class KilnExtension {

    public List<CustomTransformer> transformers = new ArrayList<>();
    public Environment environment = null;

}
