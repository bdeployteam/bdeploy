package io.bdeploy.jersey.stream;

import java.nio.file.Path;

public class StreamTestResourceImpl implements StreamTestResource {

    private Path sourceFile;
    private Path targetFile;

    public StreamTestResourceImpl(Path sourceFile) {
        this.sourceFile = sourceFile;
    }

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
