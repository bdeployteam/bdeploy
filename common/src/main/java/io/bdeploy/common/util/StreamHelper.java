package io.bdeploy.common.util;

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

}
