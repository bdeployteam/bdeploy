/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.common.collect.MoreCollectors;

/**
 * Represents a Tree of objects. Each entry is (the {@link ObjectId} of) either
 * a {@link Tree}, a {@link Manifest} or an arbitrary file blob.
 */
public class Tree implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Describes the type of each entry in the tree. There are not other magic
     * object type descriptors which aid in identifying objects in the storage, so
     * it is crucial that this is correct.
     */
    public enum EntryType {
        BLOB,
        TREE,
        MANIFEST
    }

    private final SortedMap<Key, ObjectId> children = new TreeMap<>();

    /**
     * @return all children at this hierarchy level.
     */
    public Map<Key, ObjectId> getChildren() {
        return Collections.unmodifiableMap(children);
    }

    /**
     * @param name the name of the {@link Tree} entry to retrieve.
     * @return the entry in the {@link Tree}.
     * @throws NoSuchElementException in case the entry does not exist.
     */
    public Map.Entry<Key, ObjectId> getNamedEntry(String name) {
        return children.entrySet().stream().filter(e -> e.getKey().name.equals(name)).collect(MoreCollectors.onlyElement());
    }

    /**
     * A builder to create an immutable {@link Tree}
     */
    public static final class Builder {

        private final Map<Key, ObjectId> children = new TreeMap<>();

        /**
         * Add an entry to the {@link Tree}.
         */
        public synchronized Builder add(Key key, ObjectId id) {
            if (children.containsKey(key)) {
                throw new IllegalArgumentException("duplicate entry in tree: " + key);
            }

            children.put(key, id);
            return this;
        }

        /**
         * @return the immutable {@link Tree}.
         */
        public synchronized Tree build() {
            Tree t = new Tree();
            t.children.putAll(children);
            return t;
        }

        @Override
        public String toString() {
            return "Tree(" + children.size() + ")";
        }
    }

    /**
     * A key to uniquely identify a {@link Tree} entry. Carries type information as
     * non-identifying additional meta-data. The name of an entry must be unique
     * within it's containing {@link Tree} (remember that {@link Tree}s can be
     * nested).
     */
    public static final class Key implements Serializable, Comparable<Key> {

        private static final long serialVersionUID = 1L;

        private final String name;
        private final EntryType type;

        public Key(String name, EntryType type) {
            this.name = name;
            this.type = type;
        }

        /**
         * @return the unique name of the {@link Tree} entry
         */
        public String getName() {
            return name;
        }

        /**
         * @return the type of the entry
         */
        public EntryType getType() {
            return type;
        }

        @Override
        public int compareTo(Key o) {
            return name.compareTo(o.name);
        }

        @Override
        public String toString() {
            return "[" + type + "] " + name;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((type == null) ? 0 : type.hashCode());
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
            if (type != other.type) {
                return false;
            }
            return true;
        }
    }

}
