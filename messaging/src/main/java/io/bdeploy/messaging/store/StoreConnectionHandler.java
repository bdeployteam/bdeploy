package io.bdeploy.messaging.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import jakarta.mail.event.MailEvent;
import jakarta.mail.event.MessageChangedEvent;
import jakarta.mail.event.MessageChangedListener;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.event.MessageCountListener;

//TODO re-check security features like encryption

/**
 * Handles the opening and closing of a connection to a {@link Store}, as well as basic logic associated with it.
 *
 * @param <S> The type of {@link Store} that this handler handles
 * @param <F> The type of {@link Folder} that will be connected to
 */
public abstract class StoreConnectionHandler<S extends Store, F extends Folder>//
        extends ServiceConnectionHandler<S> implements MessageReceiver {

    private static final Logger log = LoggerFactory.getLogger(StoreConnectionHandler.class);
    private static final String DEFAULT_FOLDER_NAME = "inbox";

    private final List<FolderListener> folderListeners = new ArrayList<>();
    private final List<MessageChangedListener> messageChangedListeners = new ArrayList<>();
    private final List<MessageCountListener> messageCountListeners = new ArrayList<>();

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

    /**
     * Closes the {@link Folder}. {@inheritDoc}
     */
    @Override
    public void close() {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
            } catch (MessagingException e) {
                log.error("Exception while closing  " + folder.getFullName() + " on " + getService().getURLName(), e);
            }
        }
        super.close();
    }

    @Override
    protected void afterConnect(URLName url) throws MessagingException {
        String folderName = url.getFile();

        folder = createFolder(getService(), folderName != null ? folderName : DEFAULT_FOLDER_NAME);
        folder.addConnectionListener((LoggingConnectionListener<F>) (source, info) -> log.info(info + source.getFullName()));
        folder.addMessageChangedListener(LoggingMessageChangedListener.INSTANCE);
        folder.addMessageCountListener(LoggingMessageCountListener.INSTANCE);
        folder.open(getFolderOpeningStyle().value);

        beforeListeners(folder);

        folderListeners.forEach(listener -> folder.addFolderListener(listener));
        messageChangedListeners.forEach(listener -> folder.addMessageChangedListener(listener));
        messageCountListeners.forEach(listener -> folder.addMessageCountListener(listener));
    }

    /**
     * Called before the listeners are added to the {@link Folder}.
     *
     * @param folder The {@link Folder}
     */
    protected void beforeListeners(F folder) throws MessagingException {
        // Only a hook for subclasses - does nothing by default
    }

    protected F getFolder() {
        return folder;
    }

    protected abstract F createFolder(S service, String folderName) throws MessagingException;

    protected abstract FolderOpeningStyle getFolderOpeningStyle();

    /**
     * A {@link MessageChangedListener} which logs each {@link MessageChangedEvent}.
     */
    private static class LoggingMessageChangedListener implements MessageChangedListener {

        private static LoggingMessageChangedListener INSTANCE = new LoggingMessageChangedListener();

        @Override
        public void messageChanged(MessageChangedEvent event) {
            if (!log.isTraceEnabled()) {
                return;
            }

            Message message = event.getMessage();
            int type = event.getMessageChangeType();
            String info = "(Source=" + event.getSource().toString() + ")(MsgNo=" + message.getMessageNumber() + ')';

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
                        log.trace("Could not retrieve flags of message. " + info, e);
                        return;
                    }
                    String flagInfo = "(Current flags: " + flags.toString() + ')';
                    log.trace("MessageChangedEvent.FLAGS_CHANGED received. " + info + flagInfo);
                    break;
                case MessageChangedEvent.ENVELOPE_CHANGED:
                    log.trace("Envelope of message changed. " + info);
                    break;
                default:
                    log.trace("Unknown message changed event type. " + info + "(Type=" + type + ')');
            }
        }
    }

    /**
     * A {@link MessageCountListener} which logs each {@link MessageCountEvent}.
     */
    private static class LoggingMessageCountListener implements MessageCountListener {

        private static LoggingMessageCountListener INSTANCE = new LoggingMessageCountListener();

        @Override
        public void messagesRemoved(MessageCountEvent event) {
            if (!log.isTraceEnabled()) {
                return;
            }

            int type = event.getType();
            if (type != MessageCountEvent.REMOVED) {
                log.trace("Messages removed event handler called with unexpected event type. " + getCombinedInfo(event, type));
                return;
            }
            log.trace("Removed messages from " + event.getSource().toString() + ": " + buildMessageInfo(event));
        }

        @Override
        public void messagesAdded(MessageCountEvent event) {
            if (!log.isTraceEnabled()) {
                return;
            }

            int type = event.getType();
            if (type != MessageCountEvent.ADDED) {
                log.trace("Messages added event handler called with unexpected event type. " + getCombinedInfo(event, type));
                return;
            }
            log.trace("Added messages to " + event.getSource().toString() + ": " + buildMessageInfo(event));
        }

        private static String buildMessageInfo(MessageCountEvent event) {
            StringBuilder sb = new StringBuilder();
            for (Message message : event.getMessages()) {
                int msgNo = message.getMessageNumber();
                sb.append("[(MsgNo=");
                sb.append(msgNo);
                sb.append(")(Subject=");
                String subject = "<<NotFound>>";
                try {
                    subject = message.getSubject();
                } catch (MessagingException e) {
                    log.trace("Failed to retrieve subject of message " + msgNo + ". " + getCombinedInfo(event, event.getType()),
                            e);
                }
                sb.append(subject);
                sb.append(")]");
            }
            return sb.toString();
        }

        private static String getCombinedInfo(MailEvent e, int type) {
            return "(Source=" + e.getSource().toString() + ")(Type=" + type + ')';
        }
    }

    /**
     * Defines a how a {@link Folder} shall be opened.
     */
    public enum FolderOpeningStyle {

        /**
         * @see Folder#READ_ONLY
         */
        ReadOnly(Folder.READ_ONLY),

        /**
         * @see Folder#READ_WRITE
         */
        ReadWrite(Folder.READ_WRITE);

        public final int value;

        private FolderOpeningStyle(int value) {
            this.value = value;
        }
    }
}
