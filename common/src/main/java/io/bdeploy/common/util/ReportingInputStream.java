package io.bdeploy.common.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import io.bdeploy.common.ActivityReporter.Activity;

/**
 * An input stream that reports how many bytes have been read and that calculates the transfer rate.
 */
public class ReportingInputStream extends FilterInputStream {

    private final static String ACTIVITY_TEMPLATE = "%1$s: %2$10s - %3$8s of %4$-8s - %5$s";

    private final long start = System.currentTimeMillis();

    private final String activityName;
    private final Activity activity;

    /** The total number of bytes that we expect to receive */
    private final long bytesTotal;

    /** Time when we last updated the activity */
    private long lastUpdate = System.currentTimeMillis();

    /** Bytes read since last update call */
    private long bytesToReport = 0;

    /** Number of bytes that we have already read */
    private long bytesRead;

    /**
     * Creates a new input stream that reports progress when reading from the given stream.
     *
     * @param in the input stream to read from
     * @param bytesTotal the total amount of bytes to read
     * @param activity activity reporter
     * @param activityName the name of the activity to show
     */
    public ReportingInputStream(InputStream in, long bytesTotal, Activity activity, String activityName) {
        super(in);
        this.bytesTotal = bytesTotal;
        this.activity = activity;
        this.activityName = activityName;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = in.read(b, off, len);
        if (result == -1) {
            return -1;
        }
        bytesRead += result;
        bytesToReport += result;

        long now = System.currentTimeMillis();
        long elapsedTime = now - start;
        long bytesRemaining = bytesTotal - bytesRead;

        // Update activity twice per second
        long timeSinceLastUpdate = now - lastUpdate;
        if (timeSinceLastUpdate >= 500 && elapsedTime >= 1000) {
            // If the remaining bytes are negative then we received more bytes than expected
            // We stop progress reporting as we cannot know how much more bytes are send and so we cannot calculate any statistics
            if (bytesRemaining < 0) {
                activity.activity(activityName);
            } else {
                notifyWorked(now, elapsedTime, bytesRemaining);
            }

            // Reset counters for the next update
            bytesToReport = 0;
            lastUpdate = now;
        }

        // Report progress as we read the bytes
        return result;
    }

    @Override
    public int read() throws IOException {
        int result = in.read();
        activity.worked(1);
        return result;
    }

    private void notifyWorked(long now, long elapsedTime, long bytesRemaining) {
        long remainingTimeMs = (elapsedTime * bytesRemaining) / bytesRead;
        String transferRate = FormatHelper.formatTransferRate(bytesTotal, elapsedTime);
        String fSizeRead = FormatHelper.formatFileSize(bytesRead);
        String fTotalSize = FormatHelper.formatFileSize(bytesTotal);
        String fRemaining = FormatHelper.formatRemainingTime(remainingTimeMs).trim() + " remaining";

        activity.activity(String.format(ACTIVITY_TEMPLATE, activityName, transferRate, fSizeRead, fTotalSize, fRemaining));
        activity.worked(bytesToReport);
    }

}