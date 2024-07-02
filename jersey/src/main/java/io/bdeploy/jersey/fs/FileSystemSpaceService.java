package io.bdeploy.jersey.fs;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.jersey.JerseyServer;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;

@Service
public class FileSystemSpaceService {

    private static final Logger log = LoggerFactory.getLogger(FileSystemSpaceService.class);

    @Inject
    @Named(JerseyServer.FILE_SYSTEM_MIN_SPACE)
    private Provider<Long> minFreeBytes;

    public long getFreeSpace(Path path) {
        try {
            FileStore store = Files.getFileStore(path);
            if (store == null) {
                return -1; // cannot check;
            }
            return store.getUsableSpace();
        } catch (Exception e) {
            log.error("Cannot check remaining free disc space for {}", path, e);
            return -1;
        }
    }

    /**
     * @param path the path to check
     * @return whether this path (or it's parent directory) has enough free space as configured in the MinionState's
     *         storageMinFree property.
     */
    public boolean hasFreeSpace(Path path) {
        // minFreeBytes is not bound in case of tests, etc.
        if (minFreeBytes.get() == null) {
            return true; // cannot check.
        }

        long freeSpace = getFreeSpace(path);
        if (freeSpace < 0) {
            return true; // safety fallback - we continue even though dangerous.. 
        }
        return freeSpace > minFreeBytes.get();
    }
}
