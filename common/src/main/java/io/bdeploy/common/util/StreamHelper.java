package io.bdeploy.common.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

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
     * Fully reads the given {@link InputStream} into a {@link String} using the given {@link Charset}.
     */
    public static String read(InputStream source, Charset encoding) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        try (ByteArrayOutputStream sink = new ByteArrayOutputStream()) {
            while ((n = source.read(buf)) > 0) {
                sink.write(buf, 0, n);
            }

            return sink.toString(encoding.name());
        }
    }

}
