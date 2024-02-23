package io.bdeploy.minion.mail;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.minion.MinionRoot;
import jakarta.ws.rs.core.MediaType;

/**
 * Encapsulates a given attachment data and mime type into a signed payload.
 */
public class MinionSignedAttachment {

    public static final String SIGNED_MIME_TYPE = MediaType.APPLICATION_OCTET_STREAM;
    public static final String SIGNED_SUFFIX = "-signed";

    private final String name;
    private final byte[] data;
    private final String mimeType;

    @JsonCreator
    public MinionSignedAttachment(@JsonProperty("name") String name, @JsonProperty("data") byte[] data,
            @JsonProperty("mimeType") String mimeType) {
        this.name = name;
        this.data = data;
        this.mimeType = mimeType;
    }

    public String getName() {
        return name;
    }

    public byte[] getData() {
        return data;
    }

    public String getMimeType() {
        return mimeType;
    }

    public byte[] getSigned(MinionRoot root) {
        return root.getEncryptedPayload(this).getBytes(StandardCharsets.UTF_8);
    }

    public String getSignedName() {
        return name + SIGNED_SUFFIX;
    }

    public static MinionSignedAttachment getVerified(MinionRoot root, String remoteAuth, byte[] payload) {
        return root.getDecryptedPayload(new String(payload, StandardCharsets.UTF_8), MinionSignedAttachment.class,
                root.getCertificateOfRemote(remoteAuth));
    }

}
