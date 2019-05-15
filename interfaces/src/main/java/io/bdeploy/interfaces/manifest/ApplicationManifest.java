package io.bdeploy.interfaces.manifest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;

public class ApplicationManifest implements Comparable<ApplicationManifest> {

    private static final Logger log = LoggerFactory.getLogger(ApplicationManifest.class);

    private Manifest.Key key;
    private ApplicationDescriptor desc;
    private Manifest manifest;

    public static ApplicationManifest of(BHive hive, Manifest.Key key) {
        ApplicationManifest am = new ApplicationManifest();

        am.manifest = hive.execute(new ManifestLoadOperation().setManifest(key));
        try (InputStream is = hive.execute(new TreeEntryLoadOperation().setRootTree(am.manifest.getRoot())
                .setRelativePath(ApplicationDescriptor.FILE_NAME))) {
            am.desc = StorageHelper.fromYamlStream(is, ApplicationDescriptor.class);
            am.key = key;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load application descriptor from " + key, e);
        }

        return am;
    }

    public void exportConfigTemplatesTo(BHive hive, Path target) {
        // key = target path, source = template config path in application manifest.
        for (Map.Entry<String, String> cfgFile : desc.configFiles.entrySet()) {
            String targetRelPath = cfgFile.getKey();
            String sourceRelPath = cfgFile.getValue();

            Path targetPath = target.resolve(targetRelPath);
            PathHelper.mkdirs(targetPath.getParent());

            if (Files.exists(targetPath)) {
                log.warn("WARNING: " + targetRelPath + " contributed by multiple applications");
                continue;
            }

            try (InputStream fis = hive
                    .execute(new TreeEntryLoadOperation().setRootTree(manifest.getRoot()).setRelativePath(sourceRelPath))) {
                Files.copy(fis, targetPath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to export configuration templates to " + target + " for " + key);
            }
        }
    }

    public byte[] readBrandingSplashScreen(BHive hive) {
        if (desc.branding == null || desc.branding.splash == null || desc.branding.splash.image == null) {
            return null;
        }

        try (InputStream fis = hive.execute(
                new TreeEntryLoadOperation().setRootTree(manifest.getRoot()).setRelativePath(desc.branding.splash.image));
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            copy(fis, baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read splash screen for " + key);
        }
    }

    /**
     * @see Files.copy (required due to Java 8 compat).
     */
    private static long copy(InputStream source, OutputStream sink) throws IOException {
        long nread = 0L;
        byte[] buf = new byte[8192];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;
        }
        return nread;
    }

    public ApplicationDescriptor getDescriptor() {
        return desc;
    }

    public Manifest.Key getKey() {
        return key;
    }

    @Override
    public int compareTo(ApplicationManifest o) {
        return key.compareTo(o.key);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ApplicationManifest other = (ApplicationManifest) obj;
        if (key == null) {
            if (other.key != null) {
                return false;
            }
        } else if (!key.equals(other.key)) {
            return false;
        }
        return true;
    }

}
