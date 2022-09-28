package io.bdeploy.api.product.v1;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.bdeploy.bhive.model.Manifest;

@JsonClassDescription("Describes a product (name, key, included applications, capabilities, ...). Only includes the build/version independent configuration.")
public class ProductDescriptor {

    @JsonPropertyDescription("The human readable name of the product.")
    public String name;

    /**
     * The base-key of the product. This is used to construct:
     * <ul>
     * <li>The actual key of the products {@link Manifest}. (e.g. "{product}/product")
     * <li>The actual key of included applications which are created when creating the product (e.g.
     * "{product}/applications/{app}").
     * </ul>
     */
    @JsonPropertyDescription("The (path-friendly) ID of the product, e.g. 'io.bdeploy/product'")
    public String product;

    @JsonPropertyDescription("The name of the vendor of the product.")
    public String vendor;

    @JsonPropertyDescription("A list of applications included in the product. Those applications must be available at build time, provided through 'product-version.yaml'.")
    public List<String> applications = new ArrayList<>();

    @JsonPropertyDescription("A relative path (from product-info.yaml) to a directory containing an arbitrary amount of configuration files used as templates for new instances.")
    public String configTemplates;

    @JsonPropertyDescription("A relative path (from product-info.yaml) to a directory containing plugins which are to be made available dynamically when configuring an instance using this product.")
    public String pluginFolder;

    @JsonAlias("templates") // compat, remove after 2.5.0
    @JsonPropertyDescription("A list of relative paths to ('instance-template.yaml') YAML files containing instance templates.")
    public List<String> instanceTemplates = new ArrayList<>();

    @JsonPropertyDescription("A list of relative paths to ('application-template.yaml') YAML files containing application templates.")
    public List<String> applicationTemplates = new ArrayList<>();

    @JsonPropertyDescription("A list of relative paths to ('parameter-template.yaml') YAML files containing parameter templates.")
    public List<String> parameterTemplates = new ArrayList<>();

    @JsonPropertyDescription("A list of relative paths to ('instance-variable-template.yaml') YAML files containing instance variable templates.")
    public List<String> instanceVariableTemplates = new ArrayList<>();

    @JsonPropertyDescription("A relative path to the mandatory 'product-version.yaml' file which defines versions and available applications.")
    public String versionFile;

}
