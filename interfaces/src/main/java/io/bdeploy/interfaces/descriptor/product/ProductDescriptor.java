package io.bdeploy.interfaces.descriptor.product;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.bhive.model.Manifest;

/**
 * Describes a product (name, key, included applications, capabilities, ...)
 */
public class ProductDescriptor {

    /**
     * The human readable name of the product.
     */
    public String name;

    /**
     * The base-key of the product. This is used to construct:
     * <ul>
     * <li>The actual key of the products {@link Manifest}. (e.g. "{product}/product")
     * <li>The actual key of included applications which are created when creating the product (e.g.
     * "{product}/applications/{app}").
     * </ul>
     */
    public String product;

    // TODO: add product "capabilities" later.

    // TODO: add product "instanceTemplates" later.

    /**
     * A list of application names to include. Each of the mentioned applications
     * <b>must</b> be configured in the {@link ProductVersionDescriptor}
     */
    public List<String> applications = new ArrayList<>();

    /**
     * Relative path to a directory containing configuration templates.
     * <p>
     * The directory's content is used as starting point for each instance's config directory
     */
    public String configTemplates;

    /**
     * Relative path to a file containing the {@link ProductVersionDescriptor} which may be generated or static.
     */
    public String versionFile;

}
