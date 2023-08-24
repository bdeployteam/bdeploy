package io.bdeploy.logging.process;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.zip.Deflater;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.FormattedMessage;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.NoThrowAutoCloseable;

/**
 * Processes output of a process and handles logging it to a rolling output file.
 */
public class RollingStreamGobbler extends Thread implements NoThrowAutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RollingStreamGobbler.class);

    public static final String OUT_TXT = "out.txt";
    private static final String OUT_TXT_FILEPATTERN = "out-%i.txt.gz";

    private static final String LOG_PATTERN = "%style{%d{dd-HH:mm:ss.SSS} |}{fg_#555555} %msg%n";
    private static final int LOG_MAX_INDEX = 3;

    private final Process process;
    private RollingFileAppender appender;

    private final String instance;
    private final String app;

    public RollingStreamGobbler(Path targetDir, Process process, String instance, String app) {
        this.process = process;
        this.instance = instance;
        this.app = app;

        this.appender = createFileAppender(targetDir);

        setName("Gobbler [" + instance + "/" + app + "]");
        setDaemon(true);
    }

    @Override
    public void run() {

        // this has an issue; if the inputstream is still open (application still running), this will block *forever*
        // (java.io is non-blocking and non-interruptible) in case the connected application does not write anything.

        // we just ignore the issue alltogehter, as each stream gobbler can be started only once and is
        // anyhow "lost" in case it is closed. Worst case is a thread sitting around doing nothing until
        // the application stops or writes a byte - which will make this thread die since the appender has
        // been closed on another thread.

        log(new FormattedMessage(" --- Starting output capture for {}/{}, PID: {}", instance, app, process.pid()), Level.WARN);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            br.lines().forEach(l -> log(new SimpleMessage(l), Level.INFO));
        } catch (Exception e) {
            log.debug("While redirecting process output:", e);
        }
    }

    @Override
    public synchronized void close() {
        if (isAlive()) {
            interrupt();
        }

        log(new FormattedMessage(" --- Stopping output capture for {}/{}, PID: {}", instance, app, process.pid()), Level.WARN);

        stopAppender();
    }

    private void stopAppender() {
        // need to explicitly close this to free the file handle - appender.stop is *not* enough.
        appender.getManager().close();
        appender.stop();
        appender = null;
    }

    public static void logProcessRecovery(Path targetDir, ProcessHandle handle, String instance, String app) {
        // we intentionally only perform "half" the close, by freeing up resources an *don't* care about threads
        // since we never started one.
        @SuppressWarnings("resource")
        RollingStreamGobbler gobbler = new RollingStreamGobbler(targetDir, null, instance, app);
        gobbler.log(new FormattedMessage(
                " --- Cannot resume output capture after recovery for {}/{}, PID: {} - Output will be lost until application restart.",
                instance, app, handle.pid()), Level.ERROR);
        gobbler.stopAppender();
    }

    public NoThrowAutoCloseable attachStopProcess(Process process) {
        Thread stopGobbler = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                br.lines().forEach(l -> log(new SimpleMessage("[STOP] " + l), Level.INFO));
            } catch (Exception e) {
                log.debug("While redirecting process output:", e);
            }
        }, "StopGobbler [" + instance + "/" + app + "]");
        stopGobbler.start();

        return () -> {
            if (stopGobbler.isAlive()) {
                stopGobbler.interrupt();
            }

            // cannot do more...
        };
    }

    private synchronized void log(Message msg, Level lvl) {
        if (appender == null || appender.isStopped()) {
            throw new IllegalStateException("Appender closed");
        }

        try {

            Log4jLogEvent.Builder builder = Log4jLogEvent.newBuilder();
            builder.setMessage(msg);
            builder.setLevel(lvl);

            Log4jLogEvent logEvent = builder.build();
            appender.append(logEvent);
        } catch (Exception e) {
            log.error("Cannot write to output log: {}", msg.getFormattedMessage(), e);
        }
    }

    /**
     * Creates a rolling file appender that write audit entries to a human readable log file.
     */
    private synchronized RollingFileAppender createFileAppender(Path logDir) {
        RollingFileAppender.Builder<?> builder = RollingFileAppender.newBuilder();
        builder.setName("outLogger[" + logDir + "]");
        builder.withFileName(logDir.resolve(OUT_TXT).toString());
        builder.withFilePattern(logDir.resolve(OUT_TXT_FILEPATTERN).toString());
        builder.withPolicy(SizeBasedTriggeringPolicy.createPolicy("5M"));
        builder.setLayout(PatternLayout.newBuilder().withPattern(LOG_PATTERN).build());
        builder.withStrategy(
                DefaultRolloverStrategy.newBuilder().withCompressionLevelStr(String.valueOf(Deflater.DEFAULT_COMPRESSION))
                        .withMax(Integer.toString(LOG_MAX_INDEX)).withFileIndex("min").build());
        return builder.build();
    }

}
