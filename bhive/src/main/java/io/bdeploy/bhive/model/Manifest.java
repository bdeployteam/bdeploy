/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a manifest. A manifest is a top-level object making {@link Tree}s
 * more easy to remember, associating a name and additional labels (meta-data)
 * with it.
 * <p>
 * Note that the labels are NOT part of the manifests ID.
 */
public class Manifest implements Serializable, Comparable<Manifest> {

    private static final long serialVersionUID = 1L;

    private final Key key;
    private final ObjectId root;
    private final SortedMap<String, String> labels;

    @JsonCreator
    private Manifest(@JsonProperty("key") Key key, @JsonProperty("root") ObjectId root,
            @JsonProperty("labels") SortedMap<String, String> labels) {
        this.key = key;
        this.root = root;
        this.labels = labels;
    }

    /**
     * @return the manifest key used to uniquely identify the manifest across hives
     */
    public Key getKey() {
        return key;
    }

    /**
     * @return the {@link ObjectId} of the root {@link Tree}. Always references a
     *         {@link Tree}.
     */
    public ObjectId getRoot() {
        return root;
    }

    /**
     * @return additional meta-data associated with the manifest.
     */
    public Map<String, String> getLabels() {
        return Collections.unmodifiableMap(labels);
    }

    @Override
    public int compareTo(Manifest o) {
        return key.compareTo(o.key);
    }

    @Override
    public String toString() {
        return "Manifest(" + root + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((root == null) ? 0 : root.hashCode());
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
        Manifest other = (Manifest) obj;
        if (key == null) {
            if (other.key != null) {
                return false;
            }
        } else if (!key.equals(other.key)) {
            return false;
        }
        if (root == null) {
            if (other.root != null) {
                return false;
            }
        } else if (!root.equals(other.root)) {
            return false;
        }
        return true;
    }

    /**
     * Builder to create a Manifest.
     */
    public static final class Builder {

        private final Key key;
        private ObjectId root;
        private final SortedMap<String, String> labels = new TreeMap<>();

        /**
         * @param key the immutable key of the manifest to use.
         */
        public Builder(Key key) {
            this.key = key;
        }

        /**
         * @param root the root {@link Tree} reference. The {@link ObjectId} MUST
         *            reference a {@link Tree}.
         * @return this for chaining
         */
        public Builder setRoot(ObjectId root) {
            this.root = root;
            return this;
        }

        /**
         * Add a label to the manifest. A label can be any domain/user specific
         * key/value pair.
         *
         * @param key the label key
         * @param value the label value
         * @return this for chaining
         */
        public Builder addLabel(String key, String value) {
            labels.put(key, value);
            return this;
        }

        /**
         * @return create the immutable manifest.
         */
        public Manifest build() {
            return new Manifest(key, root, labels);
        }

        public Key getKey() {
            return key;
        }

    }

    /**
     * Represents the unique ID of a manifest. Consists of a name and a tag.
     * <p>
     * Usually the tag will represent some form of version number, but there are no
     * assumptions about the actual format.
     */
    public static final class Key implements Serializable, Comparable<Key> {

        private static final long serialVersionUID = 1L;

        private final String name;
        private final String tag;

        /**
         * Creates a new key which can be used to create new manifests or lookup
         * existing manifests which match the key. Note that name and tag are used to
         * name filesystem objects ultimately, so they should not contain characters
         * which cause issues in filesystems.
         *
         * @param name the name, may not contain ':' specifically, and any
         *            filesystem-problematic character in general.
         * @param tag the tag, may not contain ':' specifically, and any
         *            filesystem-problematic character in general.
         */
        @JsonCreator
        public Key(@JsonProperty("name") String name, @JsonProperty("tag") String tag) {
            this.name = name;
            this.tag = tag;

            if (name.contains(":") || tag.contains(":")) {
                throw new IllegalArgumentException("Manifest key may not contain the ':' character: " + name + " / " + tag);
            }

            if (name.contains("\\")) {
                throw new IllegalArgumentException("Manifest key may not contain the '\\' character: " + name + " / " + tag);
            }
        }

        /**
         * @return the name of the key'd manifest
         */
        public String getName() {
            return name;
        }

        /**
         * @return the tag of the key'd manifest
         */
        public String getTag() {
            return tag;
        }

        public String directoryFriendlyName() {
            return name.replace('/', '-') + "_" + tag;
        }

        @Override
        public int compareTo(Key o) {
            int x = name.compareTo(o.name);
            if (x != 0) {
                return x;
            }

            return tag.compareTo(o.tag);
        }

        @Override
        public String toString() {
            return name + ":" + tag;
        }

        /**
         * Parses a key from it's {@link String} representation.
         */
        public static Key parse(String key) {
            int indexOf = key.indexOf(':');
            if (indexOf < 0) {
                throw new IllegalArgumentException("invalid manifest key format: " + key);
            }
            return new Key(key.substring(0, indexOf), key.substring(indexOf + 1));
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((tag == null) ? 0 : tag.hashCode());
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
            Key other = (Key) obj;
            if (name == null) {
                if (other.name != null) {
                    return false;
                }
            } else if (!name.equals(other.name)) {
                return false;
            }
            if (tag == null) {
                if (other.tag != null) {
                    return false;
                }
            } else if (!tag.equals(other.tag)) {
                return false;
            }
            return true;
        }
    }

}
