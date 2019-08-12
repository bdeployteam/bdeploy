package io.bdeploy.jersey.stream;

import java.nio.file.Path;

import io.bdeploy.jersey.JerseyPathWriter.DeleteAfterWrite;

public class StreamTestResourceImpl implements StreamTestResource {

    private final Path sourceFile;
    private Path targetFile;

    public StreamTestResourceImpl(Path sourceFile) {
        this.sourceFile = sourceFile;
    }

    @DeleteAfterWrite
    @Override
    public Path download() {
        return sourceFile;
    }

    @Override
    public void upload(Path path) {
        targetFile = path;
    }

    Path getTargetFile() {
        return targetFile;
    }

}
