package io.bdeploy.interfaces.descriptor.product;

import java.util.Map;
import java.util.TreeMap;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;

/**
 * Contains potentially dynamic information relevant when creating a product
 */
public class ProductVersionDescriptor {

    /**
     * The "version" of the product. This version is used as {@link Key#getTag() tag} for all created {@link Manifest}s
     * associated with the product (the product's own {@link Manifest} and one {@link Manifest} per application created).
     */
    public String version;

    /**
     * A mapping of application names (defined in {@link ProductDescriptor#applications}) to {@link OperatingSystem} specific
     * relative paths to app-info.yaml files (containing the relevant {@link ApplicationDescriptor}). It is assumed that the
     * app-info.yaml file is located in the root directory of the actual application build, so that the app-info.yaml's parent
     * directory is regarded as application directory to import into the product.
     */
    public Map<String, Map<OperatingSystem, String>> appInfo = new TreeMap<>();

    /**
     * A map of additional labels to add to the product manifest once generated.
     *
     * @see Manifest#getLabels()
     */
    public Map<String, String> labels = new TreeMap<>();

}
