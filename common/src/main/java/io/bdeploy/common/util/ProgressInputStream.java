package io.bdeploy.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * An extension of {@link InputStream} which allows monitoring of the read progress via listeners.
 */
public class ProgressInputStream extends InputStream {

    private final List<BiConsumer<Integer, Integer>> listeners = new ArrayList<>();
    private final InputStream in;
    private final int totalLenght;

    private int sumRead = 0;

    /**
     * @param inputStream The underlying {@link InputStream}
     * @param length The total amount of bytes that the underlying {@link InputStream} is going to read
     */
    public ProgressInputStream(InputStream inputStream, int length) {
        this.in = inputStream;
        this.totalLenght = length;
    }

    @Override
    public int read() throws IOException {
        int read = in.read();
        if (read != -1) {
            notifyListeners(1);
        }
        return read;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int readCount = in.read(b);
        notifyListeners(readCount);
        return readCount;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int readCount = in.read(b, off, len);
        notifyListeners(readCount);
        return readCount;
    }

    /**
     * {@inheritDoc}
     * <p>
     * If all bytes are read without an {@link IOException} being thrown all listeners will be called with a percentage value of
     * 100.
     */
    @Override
    public byte[] readAllBytes() throws IOException {
        byte[] allBytes = readAllBytes();
        notifyListeners(allBytes.length);
        return allBytes;
    }

    @Override
    public byte[] readNBytes(int len) throws IOException {
        byte[] nBytes = in.readNBytes(len);
        notifyListeners(nBytes.length);
        return nBytes;
    }

    @Override
    public int readNBytes(byte[] b, int off, int len) throws IOException {
        int readCount = in.readNBytes(b, off, len);
        notifyListeners(readCount);
        return readCount;
    }

    @Override
    public long skip(long n) throws IOException {
        long skip = in.skip(n);
        notifyListeners(skip);
        return skip;
    }

    /**
     * Adds a listener which is called with the amount of total bytes read, as well as the current progress of the underlying
     * {@link InputStream} in percent, rounded down to the closest {@link Integer}.
     *
     * @param listener The listener which will be added
     */
    public void addListener(BiConsumer<Integer, Integer> listener) {
        this.listeners.add(listener);
    }

    @Override
    public void close() throws IOException {
        super.close();
        in.close();
    }

    private void notifyListeners(long readCount) {
        if (readCount != -1) {
            sumRead += readCount;
        }
        int percent = sumRead == totalLenght ? 100 : (int) Math.floor(100.0 * sumRead / totalLenght);
        listeners.forEach(listener -> listener.accept(sumRead, percent));
    }
}
