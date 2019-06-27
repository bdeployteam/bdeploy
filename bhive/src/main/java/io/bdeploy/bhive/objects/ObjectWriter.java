package io.bdeploy.bhive.objects;

import java.io.IOException;
import java.nio.file.Path;

import io.bdeploy.bhive.model.ObjectId;

@FunctionalInterface
public interface ObjectWriter {

    public ObjectId write(Path t) throws IOException;

}
