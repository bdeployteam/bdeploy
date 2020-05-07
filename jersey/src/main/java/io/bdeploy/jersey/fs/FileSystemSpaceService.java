package io.bdeploy.jersey.fs;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.jersey.JerseyServer;

@Service
public class FileSystemSpaceService {

    private static final Logger log = LoggerFactory.getLogger(FileSystemSpaceService.class);

    @Inject
    @Named(JerseyServer.FILE_SYSTEM_MIN_SPACE)
    private Provider<Long> minFreeBytes;

    /**
     * @param path the path to check
     * @return whether this path (or it's parent directory) has enough free space as configured in the MinionState's
     *         storageMinFree property.
     */
    public boolean hasFreeSpace(Path path) {
        try {
            FileStore store = Files.getFileStore(path);
            if (store == null) {
                return true; // cannot check.
            }
            long free = store.getUsableSpace();
            return free > minFreeBytes.get();
        } catch (Exception e) {
            log.error("Cannot check remaining free disc space for {}", path, e);
            return true; // safety fallback - we continue even though dangerous..
        }
    }

}
