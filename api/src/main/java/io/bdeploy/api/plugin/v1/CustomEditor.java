package io.bdeploy.api.plugin.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes a custom editor plugin. A custom editor is a ES6 module loaded into the frontend.
 * <p>
 * The module must be hosted by a {@link PluginAssets}. The module path is specified as path relative to the plugins namespace
 * root.
 * <p>
 * Each {@link CustomEditor} handles a custom editor 'type'. This may be annotated on parameters in app-info.yaml files of
 * applications. If BDeploy finds a {@link CustomEditor} with a matching {@link #getTypeName()}, this custom editor will be loaded
 * on demand in the Web UI and used instead of the built-in editor.
 */
public class CustomEditor {

    private final String modulePath;
    private final String typeName;
    private final boolean allowDirectEdit;

    /**
     * @param modulePath the relative path to the plugins ES6 module file. Must be hosted by a {@link PluginAssets} in the same
     *            plugin.
     * @param typeName the type name of the editor. This is a custom string which is defined by the plugin. app-info.yaml
     *            maintainers can use this type name to request usage of this plugins {@link CustomEditor}.
     * @param allowDirectEdit whether direct/manual editing of the raw value should still be permitted.
     */
    @JsonCreator
    public CustomEditor(@JsonProperty("modulePath") String modulePath, @JsonProperty("typeName") String typeName,
            @JsonProperty("allowDirectEdit") boolean allowDirectEdit) {
        this.modulePath = modulePath;
        this.typeName = typeName;
        this.allowDirectEdit = allowDirectEdit;
    }

    /**
     * @return the path to the ES6 module
     */
    public String getModulePath() {
        return modulePath;
    }

    /**
     * @return the unique type name of this {@link CustomEditor}.
     */
    public String getTypeName() {
        return typeName;
    }

    /**
     * @return whether direct editing is still allowed even though the plugin was loaded.
     */
    public boolean isAllowDirectEdit() {
        return allowDirectEdit;
    }

}
