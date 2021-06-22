/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

import javax.annotation.Generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.util.Hex;

/**
 * Uniquely identifies a certain object. That is (typically) the content of an
 * arbitrary file which has been imported into the hive or a {@link Tree}.
 */
public class ObjectId implements Serializable, Comparable<ObjectId> {

    private static final long serialVersionUID = 1L;
    private static final int BUFFER_SIZE = 8192;
    private static final Pattern ID_PATTERN = Pattern.compile("[0-9a-f]{40}");

    private final String id;

    /**
     * Create an {@link ObjectId} from a pre-existing ID (e.g. stored on disc).
     */
    private ObjectId(String id) {
        this.id = id;
    }

    /**
     * @return the {@link String} representation of this ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Create an {@link ObjectId} from an existing {@link String} representation if
     * possible.
     *
     * @param id the {@link String} representation of the {@link ObjectId}.
     * @return the {@link ObjectId} on success, <code>null</code> otherwise.
     */
    @JsonCreator
    public static ObjectId parse(@JsonProperty("id") String id) {
        // sanity check if id is a valid ObjectId.
        if (ID_PATTERN.matcher(id).matches()) {
            return new ObjectId(id);
        }

        return null;
    }

    /**
     * Create an {@link ObjectId} by calculating the ID of the given content.
     */
    public static ObjectId create(byte[] data, int offset, int len) {
        MessageDigest digest = createDigest();
        digest.update(data, offset, len);
        return new ObjectId(Hex.bytesToHex(digest.digest()));
    }

    /**
     * Create an {@link ObjectId} by calculating the ID of the content of the given
     * source {@link InputStream}. While doing so, copy the content to the given
     * target {@link Path} as well.
     */
    public static ObjectId createByCopy(InputStream source, Path target) throws IOException {
        MessageDigest digest = createDigest();
        try (OutputStream os = Files.newOutputStream(target)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int read = 0;
            while ((read = source.read(buf)) > 0) {
                digest.update(buf, 0, read);
                os.write(buf, 0, read);
            }
        }
        return new ObjectId(Hex.bytesToHex(digest.digest()));
    }

    /**
     * Creates an {@link ObjectId} by calculating the ID of the content of the given
     * {@link InputStream}.
     * <p>
     * This method is meant for validation purposes only. To create objects in the
     * database use {@link #createByCopy(InputStream, Path)} as this copies the data
     * while hashing.
     */
    public static ObjectId createFromStreamNoCopy(InputStream source) {
        try {
            MessageDigest digest = createDigest();
            byte[] buf = new byte[BUFFER_SIZE];
            int read = 0;
            while ((read = source.read(buf)) > 0) {
                digest.update(buf, 0, read);
            }
            return new ObjectId(Hex.bytesToHex(digest.digest()));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot calculate checksum of object from stream", e);
        }
    }

    @Override
    public String toString() {
        return id;
    }

    private static MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 support is required", e);
        }
    }

    @Override
    public int compareTo(ObjectId o) {
        return id.compareTo(o.id);
    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Generated("Eclipse")
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
        ObjectId other = (ObjectId) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
    }

}
