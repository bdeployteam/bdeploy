package io.bdeploy.api.product.v1;

import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.util.OsHelper.OperatingSystem;

/**
 * Contains potentially dynamic information relevant when creating a product
 */
@JsonClassDescription("A typically generated information describing the 'current build' of a product version.")
public class ProductVersionDescriptor {

    /**
     * The "version" of the product. This version is used as {@link Key#getTag() tag} for all created {@link Manifest}s
     * associated with the product (the product's own {@link Manifest} and one {@link Manifest} per application created).
     */
    @JsonPropertyDescription("The 'version' of the product. This version is used for the product and all included applications. The format is product specific, although diversion from standard semantic versioning format may require contributing plugins with custom sorting logic.")
    @JsonProperty(required = true)
    public String version;

    /**
     * A mapping of application names (defined in {@link ProductDescriptor#applications}) to {@link OperatingSystem} specific
     * relative paths to app-info.yaml files. It is assumed that the app-info.yaml file is located in the root directory of the
     * actual application build, so that the app-info.yaml's parent directory is regarded as application directory to import into
     * the product.
     */
    @JsonPropertyDescription("Mapping of application IDs as used in 'product-info.yaml' to paths to directories per supported operating system. Multiple operating systems may point to the same path. The path may point to the directory where the 'app-info.yaml' file is located, or to the 'app-info.yaml' file directly.")
    @JsonProperty(required = true)
    public Map<String, Map<OperatingSystem, String>> appInfo = new TreeMap<>();

    /**
     * A map of additional labels to add to the product manifest once generated.
     *
     * @see Manifest#getLabels()
     */
    @JsonPropertyDescription("Additional meta-information to associate with the product. This information is shown to the user in the Web UI, and has no other use.")
    public Map<String, String> labels = new TreeMap<>();

}
