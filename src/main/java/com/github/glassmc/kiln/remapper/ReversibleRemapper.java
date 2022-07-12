package com.github.glassmc.kiln.remapper;

import com.github.glassmc.kiln.internalremapper.Remapper;

public abstract class ReversibleRemapper extends Remapper {

    public abstract ReversibleRemapper reversed();

}
