package io.bdeploy.jersey;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Handles reading/writing of large data streams while tracking progress.
 */
public class JerseyStreamingHelper {

    private JerseyStreamingHelper() {
    }

    public static void streamWithProgress(InputStream in, OutputStream out, long length) throws IOException {

        // consume no more than length
        long remaining = length <= 0 ? Long.MAX_VALUE : length;
        final byte[] buffer = new byte[1024 * 8];
        while (remaining > 0) {
            int l = in.read(buffer, 0, (int) Math.min(1024 * 8l, remaining));
            if (l == -1) {
                break;
            }
            out.write(buffer, 0, l);
            remaining -= l;
        }
    }

}
