package io.bdeploy.minion.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.interfaces.NodeStatus;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.minion.cli.RemoteMasterTool.RemoteMasterConfig;

@Help("Investigate a remote master minion")
@CliName("remote-master")
public class RemoteMasterTool extends RemoteServiceTool<RemoteMasterConfig> {

    public @interface RemoteMasterConfig {

        @Help(value = "List available minions", arg = false)
        boolean minions()

        default false;

        @Help("Path to an updated distribution (ZIP) which will be pushed to the master for update")
        String update();

        @Help(value = "Don't ask for confirmation before initiating the update process on the remote", arg = false)
        boolean yes() default false;
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

    public RemoteMasterTool() {
        super(RemoteMasterConfig.class);
    }

    @Override
    protected void run(RemoteMasterConfig config, RemoteService svc) {
        MasterRootResource client = ResourceProvider.getResource(svc, MasterRootResource.class);

        if (config.minions()) {
            SortedMap<String, NodeStatus> minions = client.getMinions();
            out().println(String.format("%1$-20s %2$-8s %3$25s %4$-10s %5$-15s", "NAME", "STATUS", "START", "OS", "VERSION"));
            for (Map.Entry<String, NodeStatus> e : minions.entrySet()) {
                NodeStatus s = e.getValue();
                String startTime = (s == null ? "" : FORMATTER.format(s.startup));
                out().println(String.format("%1$-20s %2$-8s %3$25s %4$-10s %5$-15s", e.getKey(), s == null ? "OFFLINE" : "ONLINE",
                        startTime, s == null ? "" : s.os, s == null ? "" : s.version));
            }
        } else if (config.update() != null) {
            Path zip = Paths.get(config.update());
            if (!Files.isRegularFile(zip)) {
                out().println(zip + " does not seem to be an update package");
            }

            performUpdate(config, svc, client, zip);
        }
    }

    private void performUpdate(RemoteMasterConfig config, RemoteService svc, MasterRootResource client, Path zip) {
        try {
            Manifest.Key key;
            // target hive is the default hive of the remote
            key = pushUpdate(svc, zip, getActivityReporter());

            if (!config.yes()) {
                System.out.println("Pushing of update package successful, press any key to continue updating or CTRL+C to abort");
                System.in.read();
            }

            client.update(key);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to process update", e);
        }
    }

    public static Manifest.Key pushUpdate(RemoteService remote, Path updateZipFile, ActivityReporter reporter)
            throws IOException {
        Path tmpDir = Files.createTempDirectory("update-");
        try {
            Path hive = tmpDir.resolve("hive");
            Path src = tmpDir.resolve("src");

            Manifest.Key key = calculateKeyFromDistZip(updateZipFile);

            // extract ZIP to src dir
            PathHelper.mkdirs(src);
            unzip(updateZipFile, src);

            // expecting a single directory in the ZIP containing all the things.
            Path updContent = null;
            for (Path path : Files.newDirectoryStream(src)) {
                if (Files.isDirectory(path)) {
                    if (updContent != null) {
                        throw new IllegalStateException("More than one directory in update package");
                    }
                    updContent = path;
                }
            }
            if (updContent == null) {
                throw new IllegalStateException("Cannot find update directory");
            }
            RuntimeAssert.assertTrue(Files.isDirectory(updContent), "Cannot find update content directory: " + updContent);

            Manifest.Key scopedKey = new Manifest.Key("meta/" + key.getName(), key.getTag());

            try (BHive tmpHive = new BHive(hive.toUri(), reporter)) {
                tmpHive.execute(new ImportOperation().setSourcePath(updContent).setManifest(scopedKey));
                tmpHive.execute(new PushOperation().setRemote(remote).addManifest(scopedKey));
            }
            return scopedKey;
        } finally {
            PathHelper.deleteRecursive(tmpDir);
        }
    }

    /**
     * Extract name and version from dist ZIP, as defined in gradle build.
     */
    private static Key calculateKeyFromDistZip(Path zip) {
        // check target OS for ZIP dist by checking filename.
        String name = "bdeploy";
        String lowerFN = zip.getFileName().toString().toLowerCase();

        // find a file version.properties in the ZIP
        String version = null;
        boolean snapshot = false;
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().endsWith("version.properties") && !entry.isDirectory()) {
                    Properties props = new Properties();
                    props.load(zipInputStream);

                    version = props.getProperty("version");
                    snapshot = Boolean.valueOf(props.getProperty("snapshot"));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot unzip update package", e);
        }

        if (version == null) {
            throw new IllegalStateException("Cannot determin version for update package " + zip);
        }

        if (snapshot) {
            name += "/snapshot";
        }

        // use ScopedManifestKey compatible format
        if (lowerFN.contains("linux64")) {
            name += "/linux";
        } else if (lowerFN.contains("win64")) {
            name += "/windows";
        } else {
            throw new IllegalStateException("Unsupported platform ZIP: " + lowerFN);
        }

        return new Manifest.Key(name, version);
    }

    /**
     * Unzip single ZIP file
     */
    public static void unzip(final Path zipFile, final Path target) {
        // need to use commons compress to be able to unzip file attributes
        try (ZipFile zf = new ZipFile(zipFile.toFile())) {
            Enumeration<ZipArchiveEntry> entries = zf.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                Path path = target.resolve(entry.getName());
                if (entry.isDirectory()) {
                    PathHelper.mkdirs(path);
                } else {
                    try (InputStream is = zf.getInputStream(entry)) {
                        Files.copy(is, path);
                    }
                    updatePermissions(path, entry.getUnixMode());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot unzip update package", e);
        }
    }

    private static void updatePermissions(Path file, int unixMode) throws IOException {
        if (isPosixFileStore(file)) {
            Set<PosixFilePermission> permissions = getPosixPermissionsAsSet(unixMode);
            if (!permissions.isEmpty()) {
                Files.setPosixFilePermissions(file, permissions);
            }
        }
    }

    public static boolean isPosixFileStore(Path path) throws IOException {
        return Files.getFileAttributeView(path, PosixFileAttributeView.class) != null;
    }

    public static Set<PosixFilePermission> getPosixPermissionsAsSet(int mode) {
        Set<PosixFilePermission> permissionSet = new HashSet<>();
        if ((mode & 0400) == 0400) {
            permissionSet.add(PosixFilePermission.OWNER_READ);
        }
        if ((mode & 0200) == 0200) {
            permissionSet.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((mode & 0100) == 0100) {
            permissionSet.add(PosixFilePermission.OWNER_EXECUTE);
        }
        if ((mode & 0040) == 0040) {
            permissionSet.add(PosixFilePermission.GROUP_READ);
        }
        if ((mode & 0020) == 0020) {
            permissionSet.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((mode & 0010) == 0010) {
            permissionSet.add(PosixFilePermission.GROUP_EXECUTE);
        }
        if ((mode & 0004) == 0004) {
            permissionSet.add(PosixFilePermission.OTHERS_READ);
        }
        if ((mode & 0002) == 0002) {
            permissionSet.add(PosixFilePermission.OTHERS_WRITE);
        }
        if ((mode & 0001) == 0001) {
            permissionSet.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        return permissionSet;
    }

}
