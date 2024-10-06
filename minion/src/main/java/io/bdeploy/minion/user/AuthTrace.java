package io.bdeploy.minion.user;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Helper to collect tracing message.
 */
public class AuthTrace {

    public static class Message {

        private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
        public long timestamp;
        public String text;

        public Message(String text) {
            this.timestamp = System.currentTimeMillis();
            this.text = text;
        }

        public String format() {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), TimeZone.getDefault().toZoneId()).format(dtf) + ' '
                    + text;
        }
    }

    private final boolean enabled;
    private final List<Message> messages = new ArrayList<>();

    public AuthTrace(boolean enabled) {
        this.enabled = enabled;
    }

    public void log(String text) {
        if (enabled) {
            messages.add(new Message(text));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    List<String> getMessages() {
        return messages.stream().map(Message::format).toList();
    }
}
