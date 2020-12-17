package io.bdeploy.minion.remote.jersey;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.minion.MinionRoot;

@Singleton
public class MinionStatusResourceImpl implements MinionStatusResource {

    private static final Logger log = LoggerFactory.getLogger(MinionStatusResourceImpl.class);

    @Inject
    private MinionRoot root;

    @Inject
    @Named(JerseyServer.START_TIME)
    private Instant startTime;

    @Override
    public MinionStatusDto getStatus() {
        MinionStatusDto s = new MinionStatusDto();
        s.startup = startTime;
        s.config = root.getMinionConfig();
        return s;
    }

    @Override
    public void setLoggerConfig(Path file) {
        try {
            root.updateLoggingConfiguration(f -> {
                try (InputStream is = Files.newInputStream(file)) {
                    return f.apply(is);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read updated logging configuration", e);
                }
            });
        } finally {
            PathHelper.deleteRecursive(file);
        }
    }

    @Override
    public List<RemoteDirectoryEntry> getLogEntries() {
        Path logDir = root.getLogDir();
        Path rootDir = root.getRootDir();

        List<RemoteDirectoryEntry> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(logDir)) {
            paths.filter(Files::isRegularFile).forEach(f -> {
                File file = f.toFile();
                RemoteDirectoryEntry rde = new RemoteDirectoryEntry();
                rde.path = PathHelper.separatorsToUnix(rootDir.relativize(f));
                rde.lastModified = file.lastModified();
                rde.size = file.length();
                entries.add(rde);
            });
        } catch (IOException e) {
            log.warn("Cannot read log files", e);
        }
        return entries;
    }

}
