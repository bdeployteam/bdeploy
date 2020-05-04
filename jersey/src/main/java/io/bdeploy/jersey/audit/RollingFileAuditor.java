package io.bdeploy.jersey.audit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.JsonLayout;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.util.KeyValuePair;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.StringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link RollingFileAuditor} logs audit records to a human readable log and to a programmatically readable JSON file. It must
 * be closed when it is not needed any more to release file locks.
 */
public class RollingFileAuditor implements Auditor {

    private static final Logger log = LoggerFactory.getLogger(RollingFileAuditor.class);
    public static final String LOG_PATTERN = "%d{dd-HH:mm:ss.SSS} | %-5level | AUD/%-11X{WHO} | %-7X{METHOD} | %-40X{WHAT} | %-40msg | %X{PARAMETERS}%n";
    public static final int LOG_MAX_INDEX = 3;

    public static final String LOG_TXT_FILENAME = "audit.log";
    public static final String LOG_TXT_FILEPATTERN = "audit-%i.log.gz";

    public static final String LOG_JSON_FILENAME = "audit.json";
    public static final String LOG_JSON_FILEPATTERN = "audit-%i.json.gz";

    private final Path logDir;
    private final RollingFileAppender logAppender;
    private final RollingFileAppender jsonAppender;

    public RollingFileAuditor(Path logDir) {
        this.logDir = logDir;
        this.logAppender = createFileAppender(logDir);
        this.jsonAppender = createJsonAppender(logDir);
    }

    @Override
    public String toString() {
        return "RollingFileAuditor [" + logDir + "]";
    }

    /**
     * Writes the given audit record to the file-system.
     *
     * @param record the record to write
     */
    @Override
    public void audit(AuditRecord record) {
        try {
            if (logAppender.isStopped() || jsonAppender.isStopped()) {
                return;
            }

            Log4jLogEvent.Builder builder = Log4jLogEvent.newBuilder();
            builder.setMessage(new SimpleMessage(record.message));
            switch (record.severity) {
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
            contextData.putValue("WHO", record.who);
            contextData.putValue("WHAT", record.what);
            contextData.putValue("PARAMETERS", record.parameters);
            contextData.putValue("METHOD", record.method);
            builder.setContextData(contextData);

            Log4jLogEvent logEvent = builder.build();
            jsonAppender.append(logEvent);
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
        if (jsonAppender != null) {
            jsonAppender.stop();
        }
    }

    /**
     * Returns the path to the current JSON file that is beeing used.
     *
     * @return the current JSON file
     */
    public Path getJsonFile() {
        return logDir.resolve(jsonAppender.getFileName());
    }

    public Path[] getJsonBackups() {
        List<Path> paths = new ArrayList<>();
        for (int i = 1; i <= LOG_MAX_INDEX; i++) {
            Path p = logDir.resolve(jsonAppender.getFilePattern().replace("%i", "" + i));
            if (p.toFile().exists()) {
                paths.add(p);
            }
        }
        return paths.toArray(Path[]::new);
    }

    /**
     * Returns the path to the current human readable log file that is beeing used.
     *
     * @return the current human readable log file
     */
    public Path getLogFile() {
        return logDir.resolve(logAppender.getManager().getFileName());
    }

    /**
     * Creates a rolling file appender that write audit entries to a human readable log file.
     */
    private RollingFileAppender createFileAppender(Path logDir) {
        RollingFileAppender.Builder<?> builder = RollingFileAppender.newBuilder();
        builder.setName("auditLogger");
        builder.withFileName(logDir.resolve(LOG_TXT_FILENAME).toString());
        builder.withFilePattern(logDir.resolve(LOG_TXT_FILEPATTERN).toString());
        builder.withPolicy(SizeBasedTriggeringPolicy.createPolicy("5M"));
        builder.setLayout(PatternLayout.newBuilder().withPattern(LOG_PATTERN).build());
        builder.withStrategy(
                DefaultRolloverStrategy.newBuilder().withCompressionLevelStr(String.valueOf(Deflater.DEFAULT_COMPRESSION))
                        .withMax(Integer.toString(LOG_MAX_INDEX)).build());
        return builder.build();
    }

    /**
     * Creates a rolling file appender that write audit entries to a programmatically readable JSON file.
     */
    private RollingFileAppender createJsonAppender(Path logDir) {
        List<KeyValuePair> fields = new ArrayList<>();
        fields.add(KeyValuePair.newBuilder().setKey("who").setValue("${ctx:WHO}").build());
        fields.add(KeyValuePair.newBuilder().setKey("what").setValue("${ctx:WHAT}").build());
        fields.add(KeyValuePair.newBuilder().setKey("method").setValue("${ctx:METHOD}").build());
        fields.add(KeyValuePair.newBuilder().setKey("parameters").setValue("${ctx:PARAMETERS}").build());

        RollingFileAppender.Builder<?> builder = RollingFileAppender.newBuilder();
        builder.setName("auditJsonLogger");
        builder.withFileName(logDir.resolve(LOG_JSON_FILENAME).toString());
        builder.withFilePattern(logDir.resolve(LOG_JSON_FILEPATTERN).toString());
        builder.withPolicy(SizeBasedTriggeringPolicy.createPolicy("10M"));
        builder.setLayout(JsonLayout.newBuilder().setCompact(true).setEventEol(true).setConfiguration(new DefaultConfiguration())
                .setAdditionalFields(fields.toArray(new KeyValuePair[0])).build());
        builder.withStrategy(
                DefaultRolloverStrategy.newBuilder().withCompressionLevelStr(String.valueOf(Deflater.DEFAULT_COMPRESSION))
                        .withMax(Integer.toString(LOG_MAX_INDEX)).build());
        return builder.build();
    }

}
