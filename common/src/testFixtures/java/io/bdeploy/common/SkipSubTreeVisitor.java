package io.bdeploy.common;

import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * A visitor skips a subtree having a given name. Useful when a directory and all its content should be ignored.
 */
public class SkipSubTreeVisitor extends CountingFileVisitor {

    private final Path toExclude;

    public SkipSubTreeVisitor(Path toExclude) {
        this.toExclude = toExclude;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        if (dir.endsWith(toExclude)) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
    }

}
