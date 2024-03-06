package io.bdeploy.messaging.store;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.bdeploy.messaging.LoggingConnectionListener;
import io.bdeploy.messaging.MessageReceiver;
import io.bdeploy.messaging.ServiceConnectionHandler;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import jakarta.mail.URLName;
import jakarta.mail.event.FolderListener;
import jakarta.mail.event.MessageChangedEvent;
import jakarta.mail.event.MessageChangedListener;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.event.MessageCountListener;
import jakarta.mail.event.StoreEvent;
import jakarta.mail.event.StoreListener;

/**
 * Handles the opening and closing of a connection to a {@link Store}, as well as basic logic associated with it. If no
 * {@link URLName#getFile() folder} is provided in the {@link URLName} when calling {@link #connect(URLName)} the opened
 * {@link Folder} will be {@value StoreConnectionHandler#DEFAULT_FOLDER_NAME}.
 *
 * @param <S> The type of {@link Store} that this handler handles
 * @param <F> The type of {@link Folder} that will be connected to
 */
public abstract class StoreConnectionHandler<S extends Store, F extends Folder>//
        extends ServiceConnectionHandler<S> implements MessageReceiver {

    private static final Logger log = LoggerFactory.getLogger(StoreConnectionHandler.class);
    private static final String DEFAULT_FOLDER_NAME = "inbox";

    private static final Duration KEEP_ALIVE_FREQUENCY = Duration.ofMinutes(3);
    private static final long KEEP_ALIVE_FREQUENCY_IN_SECONDS = KEEP_ALIVE_FREQUENCY.toSeconds();

    private final List<FolderListener> folderListeners = new ArrayList<>();
    private final List<MessageChangedListener> messageChangedListeners = new ArrayList<>();
    private final List<MessageCountListener> messageCountListeners = new ArrayList<>();

    private final ScheduledExecutorService keepAliveExecutor = Executors.newScheduledThreadPool(1,
            new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName() + "KeepAliveThread-%d").build());

    private ScheduledFuture<?> keepAliveSchedule;
    private F folder;

    @Override
    public void addListener(FolderListener listener) {
        this.folderListeners.add(listener);
    }

    @Override
    public void addListener(MessageChangedListener listener) {
        this.messageChangedListeners.add(listener);
    }

    @Override
    public void addListener(MessageCountListener listener) {
        this.messageCountListeners.add(listener);
    }

    @Override
    protected void modifyProperties(Properties properties) {
        properties.put("mail.store.protocol", getProtocol());
    }

    @Override
    protected void afterConnect(URLName url, boolean testMode) {
        getService().addStoreListener(LoggingStoreListener.INSTANCE);

        String folderName = url.getFile();
        try {
            folder = createFolder(getService(), folderName != null ? folderName : DEFAULT_FOLDER_NAME);
            folder.addConnectionListener(
                    (LoggingConnectionListener<F>) (source, info) -> log.info("{}{}", info, source.getFullName()));
            folder.addMessageChangedListener(LoggingMessageChangedListener.INSTANCE);
            folder.addMessageCountListener(LoggingMessageCountListener.INSTANCE);
            folder.open(getFolderOpeningMode().value);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to connect to folder " + url, e);
        }

        beforeListeners(folder);

        folderListeners.forEach(listener -> folder.addFolderListener(listener));
        messageChangedListeners.forEach(listener -> folder.addMessageChangedListener(listener));
        messageCountListeners.forEach(listener -> folder.addMessageCountListener(listener));

        if (!testMode) {
            keepAliveSchedule = keepAliveExecutor.scheduleWithFixedDelay(this::keepAlive, KEEP_ALIVE_FREQUENCY_IN_SECONDS,
                    KEEP_ALIVE_FREQUENCY_IN_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Override
    public void disconnect() {
        if (keepAliveSchedule != null && !keepAliveSchedule.isDone()) {
            keepAliveSchedule.cancel(false);
            keepAliveSchedule = null;
        }
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
                folder = null;
            } catch (MessagingException e) {
                log.error("Exception while closing  " + getFolderAndUrlLogString(), e);
            }
        }
        super.disconnect();
    }

    @Override
    public void close() {
        keepAliveExecutor.close();
        super.close();
    }

    protected void keepAlive() {
        S service = getService();
        if (!service.isConnected()) {
            try {
                service.connect();
            } catch (MessagingException e) {
                log.error("Failed to connect to " + service.getURLName(), e);
                return;
            }
        }

        if (!folder.isOpen()) {
            try {
                folder.open(getFolderOpeningMode().value);
            } catch (MessagingException e) {
                log.error("Failed to open " + getFolderAndUrlLogString(), e);
                return;
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("Made sure that folder is open | {}", getFolderAndUrlLogString());
        }
    }

    /**
     * Called before the listeners are added to the {@link Folder}.
     *
     * @param folder The {@link Folder}
     */
    protected void beforeListeners(F folder) {
        // Only a hook for subclasses - does nothing by default
    }

    protected F getFolder() {
        return folder;
    }

    protected String getFolderAndUrlLogString() {
        return "folder " + folder.getFullName() + " on " + getService().getURLName();
    }

    protected abstract F createFolder(S service, String folderName) throws MessagingException;

    protected abstract FolderOpeningMode getFolderOpeningMode();

    /**
     * A {@link StoreListener} which logs each {@link StoreEvent} at trace level.
     */
    private static class LoggingStoreListener implements StoreListener {

        private static final LoggingStoreListener INSTANCE = new LoggingStoreListener();

        @Override
        public void notification(StoreEvent event) {
            if (!log.isTraceEnabled()) {
                return;
            }

            String messageAndSource = event.getMessage() + " (Source=" + event.getSource().toString() + ')';
            int messageType = event.getMessageType();
            switch (messageType) {
                case StoreEvent.NOTICE:
                    log.trace("Notice: {}", messageAndSource);
                    return;
                case StoreEvent.ALERT:
                    log.trace("Alert: {}", messageAndSource);
                    return;
                default:
                    log.trace("Unknown {} with message type {}. Message: {}", StoreEvent.class.getSimpleName(), messageType,
                            messageAndSource);
            }
        }
    }

    /**
     * A {@link MessageChangedListener} which logs each {@link MessageChangedEvent} at trace level.
     */
    private static class LoggingMessageChangedListener implements MessageChangedListener {

        private static final LoggingMessageChangedListener INSTANCE = new LoggingMessageChangedListener();

        @Override
        public void messageChanged(MessageChangedEvent event) {
            if (!log.isTraceEnabled()) {
                return;
            }

            Message message = event.getMessage();
            int type = event.getMessageChangeType();
            String info = "(Source=" + event.getSource() + ")(MsgNo=" + message.getMessageNumber() + ')';

            String subjectInfo = "";
            try {
                subjectInfo = "(Subject=" + message.getSubject() + ')';
            } catch (MessagingException e) {
                log.trace("Could not retrieve subject of message. " + info, e);
            }
            info += subjectInfo;

            switch (type) {
                case MessageChangedEvent.FLAGS_CHANGED:
                    //Note that this event may also get triggered without an actual change in the flags
                    Flags flags;
                    try {
                        flags = message.getFlags();
                    } catch (MessagingException e) {
                        log.trace("Could not retrieve flags of message: " + info, e);
                        return;
                    }
                    String flagInfo = "(Current flags: " + flags.toString() + ')';
                    log.trace("MessageChangedEvent.FLAGS_CHANGED received: {}{}", info, flagInfo);
                    break;
                case MessageChangedEvent.ENVELOPE_CHANGED:
                    log.trace("Envelope of message changed: {}", info);
                    break;
                default:
                    log.trace("Unknown message changed event type: {}(Type={})", info, type);
            }
        }
    }

    /**
     * A {@link MessageCountListener} which logs each {@link MessageCountEvent} at trace level.
     */
    private static class LoggingMessageCountListener implements MessageCountListener {

        private static final LoggingMessageCountListener INSTANCE = new LoggingMessageCountListener();

        @Override
        public void messagesRemoved(MessageCountEvent event) {
            if (!log.isTraceEnabled()) {
                return;
            }

            String source = event.getSource().toString();
            int type = event.getType();
            if (type != MessageCountEvent.REMOVED) {
                log.trace("Messages removed event handler called with unexpected event type. (Source={})(Type={})", source, type);
                return;
            }
            log.trace("Removed messages from {}: {}", source, buildMessageInfo(event));
        }

        @Override
        public void messagesAdded(MessageCountEvent event) {
            if (!log.isTraceEnabled()) {
                return;
            }

            String source = event.getSource().toString();
            int type = event.getType();
            if (type != MessageCountEvent.ADDED) {
                log.trace("Messages added event handler called with unexpected event type. (Source={})(Type=)", source, type);
                return;
            }
            log.trace("Added messages to {}: {}", source, buildMessageInfo(event));
        }

        private static String buildMessageInfo(MessageCountEvent event) {
            StringBuilder sb = new StringBuilder();
            for (Message message : event.getMessages()) {
                int msgNo = message.getMessageNumber();
                sb.append("[(MsgNo=");
                sb.append(msgNo);
                sb.append(')');
                if (!message.isExpunged()) {
                    sb.append("(Subject=");
                    String subject = "<<NotFound>>";
                    try {
                        subject = message.getSubject();
                    } catch (MessagingException e) {
                        log.trace("Failed to retrieve subject of message " + msgNo + '.', e);
                    }
                    sb.append(subject);
                    sb.append(')');
                }
                sb.append(']');
            }
            return sb.toString();
        }
    }

    /**
     * Defines a how a {@link Folder} shall be opened.
     */
    public enum FolderOpeningMode {

        /**
         * @see Folder#READ_ONLY
         */
        READ_ONLY(Folder.READ_ONLY),

        /**
         * @see Folder#READ_WRITE
         */
        READ_WRITE(Folder.READ_WRITE);

        public final int value;

        private FolderOpeningMode(int value) {
            this.value = value;
        }
    }
}
