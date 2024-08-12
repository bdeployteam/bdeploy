package io.bdeploy.ui;

import java.io.IOException;
import java.io.InputStream;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.util.RuntimeAssert;
import jakarta.ws.rs.WebApplicationException;

/**
 * Aids in wrapping and unwrapping from multipart/form-data content.
 */
public class FormDataHelper {

    private FormDataHelper() {
    }

    public static FormDataMultiPart createMultiPartForStream(String name, InputStream stream) {
        FormDataMultiPart fdmp = new FormDataMultiPart();
        fdmp.bodyPart(new StreamDataBodyPart(name, stream));
        return fdmp;
    }

    public static InputStream getStreamFromMultiPart(MultiPart mp) {
        RuntimeAssert.assertEquals(1, mp.getBodyParts().size(), "Expecting exactly one body part");
        return mp.getBodyParts().get(0).getEntityAs(InputStream.class);
    }

    public static <T> T getYamlEntityFromMultiPart(MultiPart mp, Class<T> target) {
        try (InputStream is = getStreamFromMultiPart(mp)) {
            return StorageHelper.fromYamlStream(is, target);
        } catch (IOException e) {
            throw new WebApplicationException("Cannot data from multi-part", e);
        }
    }
}
