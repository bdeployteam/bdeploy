package io.bdeploy.api.product.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.common.util.OsHelper.OperatingSystem;

public class ApplicationDescriptorApi {

    /**
     * The name under which the {@link ApplicationDescriptorApi} can be found in a
     * conforming application folder.
     */
    public static final String FILE_NAME = "app-info.yaml";

    /**
     * Operating systems which are supported by this app-info.yaml.
     * <p>
     * The purpose of this information is solely verification of product configuration
     * during creation of products which include platform specific applications.
     */
    @JsonPropertyDescription("The supported operating systems this application can run on.")
    public List<OperatingSystem> supportedOperatingSystems = new ArrayList<>();

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
    @JsonPropertyDescription("A set of dependencies which must be included in the product along with the application. These dependencies can be referenced from commands, parameters, etc. using variable expansion.")
    public SortedSet<String> runtimeDependencies = new TreeSet<>();

}
