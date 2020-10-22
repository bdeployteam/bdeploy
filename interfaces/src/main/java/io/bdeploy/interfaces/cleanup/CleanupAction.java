package io.bdeploy.interfaces.cleanup;

import java.nio.file.Path;

import javax.ws.rs.core.SecurityContext;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;

/**
 * Represents a single action to be performed during cleanup.
 */
public class CleanupAction {

    public enum CleanupType {
        DELETE_MANIFEST,
        DELETE_FOLDER,
        UNINSTALL_INSTANCE_VERSION
    }

    /**
     * The type of cleanup to perform.
     */
    public CleanupType type;

    /**
     * What to operate on. Might be a representation of a {@link Manifest} {@link Key} or a {@link Path} depending on the
     * {@link #type}.
     */
    public String what;

    /**
     * Human readable additional description to tell the user what is happening.
     */
    public String description;

    @JsonCreator
    public CleanupAction(@JsonProperty("type") CleanupType type, @JsonProperty("what") String what,
            @JsonProperty("description") String description) {
        this.type = type;
        this.what = what;
        this.description = description;
    }

    @Override
    public String toString() {
        return type.name() + " " + what;
    }

    public void execute(SecurityContext context, MasterProvider provider, BHive hive) {
        switch (type) {
            case UNINSTALL_INSTANCE_VERSION:
                Key imKey = Key.parse(what);
                InstanceGroupConfiguration igc = new InstanceGroupManifest(hive).read();

                MasterRootResource root = ResourceProvider.getVersionedResource(provider.getControllingMaster(hive, imKey),
                        MasterRootResource.class, context);
                root.getNamedMaster(igc.name).uninstall(imKey);
                break;
            case DELETE_MANIFEST:
                hive.execute(new ManifestDeleteOperation().setToDelete(Key.parse(what)));
                break;
            default:
                throw new IllegalStateException("CleanupType " + type + " not supported here");
        }

    }
}
