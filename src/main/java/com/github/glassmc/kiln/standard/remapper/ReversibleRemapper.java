package com.github.glassmc.kiln.standard.remapper;

import com.github.glassmc.kiln.standard.internalremapper.Remapper;

public abstract class ReversibleRemapper extends Remapper {

    public abstract ReversibleRemapper reversed();

}
