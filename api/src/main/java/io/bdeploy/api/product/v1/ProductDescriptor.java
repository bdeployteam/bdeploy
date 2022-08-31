package io.bdeploy.api.product.v1;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

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

    /**
     * The name of the vendor of the product.
     */
    public String vendor;

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
     * Relative path to a directory containing product-bound plugins.
     * <p>
     * These plugins are loaded into the server on demand when configuring applications for this product. They can define
     * additional editor controls for certain parameters, etc.
     */
    public String pluginFolder;

    /**
     * A list of relative paths to YAML files containing instance templates.
     */
    @JsonAlias("templates") // compat, remove after 2.5.0
    public List<String> instanceTemplates = new ArrayList<>();

    /**
     * A list of relative paths to YAML files containing application templates.
     */
    public List<String> applicationTemplates = new ArrayList<>();

    /**
     * A list of relative paths to YAML files containing parameter templates.
     */
    public List<String> parameterTemplates = new ArrayList<>();

    /**
     * Relative path to a file containing the {@link ProductVersionDescriptor} which may be generated or static.
     */
    public String versionFile;

}
