package io.bdeploy.minion.user;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper to collect tracing message.
 */
public class AuthTrace {

    public static class Message {

        private transient final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        public long timestamp;
        public String text;

        public Message(String text) {
            this.timestamp = System.currentTimeMillis();
            this.text = text;
        }

        public String format() {
            return sdf.format(new Date(timestamp)) + " " + text;
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
        return messages.stream().map(m -> m.format()).collect(Collectors.toList());
    }
}
