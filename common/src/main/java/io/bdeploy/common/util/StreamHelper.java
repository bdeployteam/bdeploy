package io.bdeploy.common.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamHelper {

    private StreamHelper() {
    }

    /**
     * Copies the given input stream to the given output stream.
     */
    public static long copy(InputStream source, OutputStream sink) throws IOException {
        long nread = 0L;
        byte[] buf = new byte[8192];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
            nread += n;
        }
        return nread;
    }

    /**
     * Fully reads the given {@link InputStream} into a byte[].
     */
    public static byte[] read(InputStream source) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        try (ByteArrayOutputStream sink = new ByteArrayOutputStream()) {
            while ((n = source.read(buf)) > 0) {
                sink.write(buf, 0, n);
            }

            return sink.toByteArray();
        }
    }

    /**
     * Determine whether a file is text or binary. This is done by scanning for 'zero' bytes in the contents.
     */
    public static boolean isTextFile(InputStream is) throws IOException {
        // there is no reliable way to determine this. we're using a similar heuristic as `grep`.
        // we scan for zero bytes in the file in a limited set of bytes in case the file is large.
        int remainingToCheck = 4096; // only check the first 4k.
        while (remainingToCheck-- > 0) {
            int b = is.read();
            if (b == -1) {
                break;
            }
            if (b == 0) {
                return false;
            }
        }

        // EOF or end of region to check without encountering zero byte
        return true;
    }

}
