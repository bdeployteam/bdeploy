package io.bdeploy.common;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class CountingFileVisitor extends SimpleFileVisitor<Path> {

    long fileCount = 0;

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        fileCount++;
        return FileVisitResult.CONTINUE;
    }

    public long getFileCount() {
        return fileCount;
    }

}
