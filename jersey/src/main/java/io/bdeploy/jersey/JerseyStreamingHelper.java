package io.bdeploy.jersey;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;

/**
 * Handles reading/writing of large data streams while tracking progress.
 */
public class JerseyStreamingHelper {

    public enum StreamDirection {
        READ,
        WRITE
    }

    public static void streamWithProgress(ActivityReporter reporter, StreamDirection direction, InputStream in, OutputStream out,
            long length) throws IOException {
        long maxWork = Math.max(0, length) / 1024;

        Activity stream = reporter
                .start((direction == StreamDirection.READ ? "Reading... " : "Writing... ") + " (" + maxWork + "KB)...", maxWork);

        try {
            // consume no more than length
            long remaining = length <= 0 ? Long.MAX_VALUE : length;
            final byte[] buffer = new byte[1024];
            while (remaining > 0) {
                int l = in.read(buffer, 0, (int) Math.min(1024, remaining));
                if (l == -1) {
                    break;
                }
                out.write(buffer, 0, l);
                remaining -= l;
                stream.workAndCancelIfRequested(1);
            }
        } finally {
            stream.done();
        }
    }

}
