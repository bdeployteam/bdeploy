package io.bdeploy.interfaces.manifest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.annotation.processing.Generated;

import io.bdeploy.api.product.v1.ApplicationDescriptorApi;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;

public class ApplicationManifest implements Comparable<ApplicationManifest> {

    private Manifest.Key key;
    private ApplicationDescriptor desc;
    private Manifest manifest;

    public static ApplicationManifest of(BHive hive, Manifest.Key key) {
        ApplicationManifest am = new ApplicationManifest();

        am.manifest = hive.execute(new ManifestLoadOperation().setManifest(key));
        try (InputStream is = hive.execute(new TreeEntryLoadOperation().setRootTree(am.manifest.getRoot())
                .setRelativePath(ApplicationDescriptorApi.FILE_NAME))) {
            am.desc = StorageHelper.fromYamlStream(is, ApplicationDescriptor.class);
            am.key = key;

            // make sure configuration is consistent.
            am.desc.fixupDefaults();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load application descriptor from " + key, e);
        }

        return am;
    }

    public byte[] readBrandingSplashScreen(BHive hive) {
        if (desc.branding == null || desc.branding.splash == null || desc.branding.splash.image == null) {
            return null;
        }

        TreeEntryLoadOperation loadSplash = new TreeEntryLoadOperation().setRootTree(manifest.getRoot())
                .setRelativePath(desc.branding.splash.image);
        try (InputStream fis = hive.execute(loadSplash); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            StreamHelper.copy(fis, baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read splash screen for " + key);
        }
    }

    public byte[] readBrandingIcon(BHive hive) {
        if (desc.branding == null || desc.branding.icon == null) {
            return null;
        }
        TreeEntryLoadOperation loadIcon = new TreeEntryLoadOperation().setRootTree(manifest.getRoot())
                .setRelativePath(desc.branding.icon);
        try (InputStream fis = hive.execute(loadIcon); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            StreamHelper.copy(fis, baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read icon for " + key);
        }
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

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        return result;
    }

    @Generated("Eclipse")
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
