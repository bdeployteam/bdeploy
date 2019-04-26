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

/**
 * The {@link RollingFileAuditor} logs audit records to a human readable log and to a programmatically readable JSON file. It must
 * be closed when it is not needed any more to release file locks.
 */
public class RollingFileAuditor implements Auditor {

    private final static String LOG_PATTERN = "%d{dd-HH:mm:ss.SSS} | %-5level | AUD/%-11X{WHO} | %-7X{METHOD} | %-40X{WHAT} | %-40msg | %X{PARAMETERS}%n";

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
        if (logAppender.isStopped() || logAppender.isStopped()) {
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

    /**
     * Returns the path to the current JSON file that is beeing used.
     *
     * @return the current JSON file
     */
    public Path getLogFile() {
        return logDir.resolve(logAppender.getManager().getFileName());
    }

    /**
     * Creates a rolling file appender that write audit entries to a human readable log file.
     */
    private RollingFileAppender createFileAppender(Path logDir) {
        RollingFileAppender.Builder<?> builder = RollingFileAppender.newBuilder();
        builder.withName("auditLogger");
        builder.withFileName(logDir.resolve("audit.log").toString());
        builder.withFilePattern(logDir.resolve("audit-%i.log.gz").toString());
        builder.withPolicy(SizeBasedTriggeringPolicy.createPolicy("5M"));
        builder.withLayout(PatternLayout.newBuilder().withPattern(LOG_PATTERN).build());
        builder.withStrategy(DefaultRolloverStrategy.newBuilder()
                .withCompressionLevelStr(String.valueOf(Deflater.DEFAULT_COMPRESSION)).withMax("3").build());
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
        builder.withName("auditJsonLogger");
        builder.withFileName(logDir.resolve("audit.json").toString());
        builder.withFilePattern(logDir.resolve("audit-%i.json.gz").toString());
        builder.withPolicy(SizeBasedTriggeringPolicy.createPolicy("10M"));
        builder.withLayout(JsonLayout.newBuilder().setCompact(true).setEventEol(true).setConfiguration(new DefaultConfiguration())
                .setAdditionalFields(fields.toArray(new KeyValuePair[0])).build());
        builder.withStrategy(DefaultRolloverStrategy.newBuilder()
                .withCompressionLevelStr(String.valueOf(Deflater.DEFAULT_COMPRESSION)).withMax("3").build());
        return builder.build();
    }

}
