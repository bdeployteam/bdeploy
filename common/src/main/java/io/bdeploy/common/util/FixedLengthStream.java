package io.bdeploy.common.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream that reads a given amount of bytes from a stream.
 */
public class FixedLengthStream extends InputStream {

    /** The underlying stream to read from */
    private final InputStream in;

    /** The number of bytes to read from the stream */
    private long remaining;

    /**
     * Creates a new input stream that reads the given amount from the given stream.
     *
     * @param in the stream to read from
     * @param totalSize the number of bytes to read
     */
    public FixedLengthStream(InputStream in, long totalSize) {
        this.in = in;
        this.remaining = totalSize;
    }

    @Override
    public int read() throws IOException {
        byte[] single = new byte[1];
        int num = read(single, 0, 1);
        return num == -1 ? -1 : (single[0] & 0xFF);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        // Signal end of stream if we have consumed all bytes
        if (remaining <= 0) {
            return -1;
        }

        // Read up-to the remaining size
        long bytesToRead = Math.min(len, remaining);
        int numRead = in.read(b, off, (int) bytesToRead);
        if (numRead == -1) {
            throw new IOException("Unexpected end of stream. Expecting '" + remaining + "' bytes.");
        }

        // Provide the number of bytes that where read
        remaining -= numRead;
        return numRead;
    }

}
