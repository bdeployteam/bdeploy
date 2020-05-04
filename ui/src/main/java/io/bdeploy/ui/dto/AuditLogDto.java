package io.bdeploy.ui.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AuditLogDto {

    protected static class AuditInstant {

        long epochSecond;
        long nanoOfSecond;
    }

    public Instant instant;
    public boolean endOfBatch;

    public String thread;
    public long threadId;
    public int threadPriority;

    public String level;
    public String message;

    public String who;
    public String what;
    public String method;
    public String parameters;

    // Log4J maps Instant as Object, e.g.: "instant":{"epochSecond":1587639012,"nanoOfSecond":8969000}
    // The bdeploy object mapper is configured to map time classes to log

    @JsonCreator
    public AuditLogDto( //
            @JsonProperty("instant") AuditInstant instant, //
            @JsonProperty("endOfBatch") boolean endOfBatch, //
            @JsonProperty("thread") String thread, //
            @JsonProperty("threadId") long threadId, //
            @JsonProperty("threadPriority") int threadPriority, //
            @JsonProperty("level") String level, //
            @JsonProperty("message") String message, //
            @JsonProperty("who") String who, //
            @JsonProperty("what") String what, //
            @JsonProperty("method") String method, //
            @JsonProperty("parameters") String parameters) //
    {
        this.instant = Instant.ofEpochSecond(instant.epochSecond, instant.nanoOfSecond);
        this.endOfBatch = endOfBatch;

        this.thread = thread;
        this.threadId = threadId;
        this.threadPriority = threadPriority;

        this.level = level;
        this.message = message;

        this.who = who;
        this.what = what;
        this.method = method;
        this.parameters = parameters;

    }
}