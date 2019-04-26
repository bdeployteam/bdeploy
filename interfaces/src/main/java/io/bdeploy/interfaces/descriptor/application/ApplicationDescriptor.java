package io.bdeploy.interfaces.descriptor.application;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.interfaces.ScopedManifestKey;

/**
 * Top level element defining an application. The serialized form of this DTO
 * <b>must</b> be present for any application which wishes to be controlled by
 * the PCU.
 * <p>
 * The serialized form of this DTO must reside in the top-level or any
 * application imported into the system, and have the name {@value #FILE_NAME}.
 */
public class ApplicationDescriptor implements Comparable<ApplicationDescriptor> {

    /**
     * The name under which the {@link ApplicationDescriptor} can be found in a
     * conforming application folder.
     */
    public static final String FILE_NAME = "app-info.yaml";

    public enum ApplicationType {
        SERVER,
        CLIENT;
    }

    /**
     * User friendly name of the application
     */
    public String name;

    /**
     * Type of application.
     */
    public ApplicationType type = ApplicationType.SERVER;

    /**
     * Operating systems which are supported by this app-info.yaml.
     * <p>
     * The purpose of this information is solely verification of product configuration
     * during creation of products which include platform specific applications.
     */
    public List<OperatingSystem> supportedOperatingSystems = new ArrayList<>();

    /**
     * Describes the process control specific properties of this application.
     */
    public ProcessControlDescriptor processControl = new ProcessControlDescriptor();

    /**
     * Description of command used to start an application.
     */
    public ExecutableDescriptor startCommand;

    /**
     * Description of command used to stop and application. This is optional. If not
     * given, the existing process is destroyed (which usually gives it chance to
     * shut down properly).
     */
    public ExecutableDescriptor stopCommand;

    /**
     * Additional configuration files which are required.
     * <p>
     * Key is the target path in the shared config directory per deployment.
     * <p>
     * Value is the relative path to a default config file which is copied as a
     * starting template.
     */
    public SortedMap<String, String> configFiles = new TreeMap<>();

    /**
     * Additional dependencies of the application.
     * <p>
     * Runtime dependencies are resolved at two points in time:
     * <ul>
     * <li>When creating a product which includes this application. The product always includes applications
     * for a specific operating system, thus this operating system is used to resolve dependencies.</li>
     * <li>When creating an InstanceNodeManifest which includes this application for a specific (the node's)
     * operating system, in which case the dependency is resolved against the actual OS.</li>
     * </ul>
     * <p>
     * The dependency <b>must</b> include a name and a tag. This name and tag are used to
     * construct {@link ScopedManifestKey}s which are os specific, using the OS provided
     * by the current context.
     */
    public SortedSet<String> runtimeDependencies = new TreeSet<>();

    @Override
    public int compareTo(ApplicationDescriptor o) {
        return name.compareTo(o.name);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
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
        ApplicationDescriptor other = (ApplicationDescriptor) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

}
