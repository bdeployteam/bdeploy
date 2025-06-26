package io.bdeploy.logging.audit;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.zip.Deflater;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.StringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.audit.AuditRecord;
import io.bdeploy.common.audit.Auditor;
import io.bdeploy.common.audit.NullAuditor;
import io.bdeploy.common.util.ZipHelper;

/**
 * The {@link RollingFileAuditor} logs audit records to a rolling log file. It must
 * be closed when it is not needed any more to release file locks.
 */
public class RollingFileAuditor implements Auditor {

    private static final Logger log = LoggerFactory.getLogger(RollingFileAuditor.class);
    public static final String LOG_PATTERN = "%d{yyyy-MM-dd-HH:mm:ss.SSS} | %-5level | AUD/%-20X{WHO} | %-7X{METHOD} | %-40X{WHAT} | %-40msg | %X{PARAMETERS}%n";
    public static final int LOG_MAX_INDEX = 3;

    public static final String LOG_TXT_FILENAME = "audit.log";
    public static final String LOG_TXT_FILEPATTERN = "audit-%i.log.gz";

    private final Path logDir;
    private final RollingFileAppender logAppender;

    private RollingFileAuditor(Path logDir) {
        this.logDir = logDir;
        this.logAppender = createFileAppender(logDir);
    }

    /**
     * Returns a {@link RollingFileAuditor} if the given path can be written to. The auditor will audit in the "logs"
     * subdirectory.
     * <p>
     * If the path is not writable (e.g. inside a ZIP file), a {@link NullAuditor} is returned instead.
     */
    public static Function<Path, Auditor> getFactory() {
        return p -> {
            if (ZipHelper.isZipUri(p.toUri())) {
                return new NullAuditor();
            } else {
                return getInstance(p.resolve("logs"));
            }
        };
    }

    /**
     * Returns a {@link RollingFileAuditor} for the given {@link Path}. The path is assumed to be writable. Logs will be written
     * directly into this path.
     */
    public static RollingFileAuditor getInstance(Path p) {
        return new RollingFileAuditor(p);
    }

    @Override
    public String toString() {
        return "RollingFileAuditor [" + logDir + "]";
    }

    /**
     * Writes the given audit record to the file-system.
     *
     * @param rec the record to write
     */
    @Override
    public void audit(AuditRecord rec) {
        try {
            if (logAppender.isStopped()) {
                return;
            }

            Log4jLogEvent.Builder builder = Log4jLogEvent.newBuilder();
            builder.setMessage(new SimpleMessage(rec.message));
            switch (rec.severity) {
                case NORMAL:
                    builder.setLevel(Level.INFO);
                    break;
                case WARNING:
                    builder.setLevel(Level.WARN);
                    break;
                case ERROR:
                    builder.setLevel(Level.ERROR);
                    break;
            }

            StringMap contextData = ContextDataFactory.createContextData();
            contextData.putValue("WHO", rec.who);
            contextData.putValue("WHAT", rec.what);
            contextData.putValue("PARAMETERS", rec.parameters);
            contextData.putValue("METHOD", rec.method);
            builder.setContextData(contextData);

            Log4jLogEvent logEvent = builder.build();
            logAppender.append(logEvent);
        } catch (Exception e) {
            log.error("Cannot write audit log", e);
        }
    }

    @Override
    public void close() {
        if (logAppender != null) {
            logAppender.stop();
        }
    }

    /**
     * Returns the path to the current JSON file that is beeing used.
     *
     * @return the current JSON file
     */
    public Path getLogDir() {
        return logDir;
    }

    /**
     * Creates a rolling file appender that write audit entries to a human readable log file.
     */
    private static RollingFileAppender createFileAppender(Path logDir) {
        RollingFileAppender.Builder<?> builder = RollingFileAppender.newBuilder();
        builder.setName("auditLogger");
        builder.withFileName(logDir.resolve(LOG_TXT_FILENAME).toString());
        builder.withFilePattern(logDir.resolve(LOG_TXT_FILEPATTERN).toString());
        builder.withPolicy(SizeBasedTriggeringPolicy.createPolicy("5M"));
        builder.setLayout(PatternLayout.newBuilder().withPattern(LOG_PATTERN).build());
        builder.withStrategy(
                DefaultRolloverStrategy.newBuilder().withCompressionLevelStr(String.valueOf(Deflater.DEFAULT_COMPRESSION))
                        .withMax(Integer.toString(LOG_MAX_INDEX)).withFileIndex("min").build());
        return builder.build();
    }

}
