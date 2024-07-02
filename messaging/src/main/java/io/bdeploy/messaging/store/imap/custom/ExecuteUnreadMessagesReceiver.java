package io.bdeploy.messaging.store.imap.custom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.messaging.MessageDataHolder;
import io.bdeploy.messaging.store.imap.IMAPStoreConnectionHandler;
import io.bdeploy.messaging.util.MessageDataHolderBuilder;
import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.URLName;
import jakarta.mail.event.MessageChangedEvent;
import jakarta.mail.event.MessageChangedListener;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.event.MessageCountListener;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.SearchTerm;

/**
 * An extension of {@link IMAPStoreConnectionHandler} which introduces listeners who react to all messages whose {@link Flag#SEEN
 * "seen"-flag} is set to <code>false</code> at the following events:
 * <ul>
 * <li>Once for all messages whose "seen"-flag is false upon initially calling {@link #connect(URLName)}</li>
 * <li>A new message arrived in the folder and its "seen"-flag is <code>false</code></li>
 * <li>An existing message in the folder got its "seen" flag to <code>false</code></li>
 * </ul>
 * The {@link Flag#SEEN "seen"-flag} is updated to <code>true</code> after the listeners got called if no {@link RuntimeException}
 * occurred.
 */
public class ExecuteUnreadMessagesReceiver extends IMAPStoreConnectionHandler {

    private static final Logger log = LoggerFactory.getLogger(ExecuteUnreadMessagesReceiver.class);
    private static final Comparator<Message> OLDEST_MESSAGE_FIRST_COMPARATOR = (msg1, msg2) -> {
        Date date1;
        try {
            date1 = msg1.getSentDate();
        } catch (MessagingException e) {
            log.error("Could not determine received date message " + msg1.getMessageNumber() + ". Comparator will return 0.", e);
            return 0;
        }
        Date date2;
        try {
            date2 = msg2.getSentDate();
        } catch (MessagingException e) {
            log.error("Could not determine received date message " + msg2.getMessageNumber() + ". Comparator will return 0.", e);
            return 0;
        }
        return date1.compareTo(date2);
    };
    private static final FlagTerm UNSEEN_MESSAGES_FLAG_TERM = new FlagTerm(new Flags(Flag.SEEN), false);

    private final List<Consumer<MessageDataHolder>> listeners = new ArrayList<>();

    private SearchTerm filter = UNSEEN_MESSAGES_FLAG_TERM;

    public ExecuteUnreadMessagesReceiver() {
        super(FolderOpeningMode.READ_WRITE);
        addListener(new ExecuteOnNewListener());
        addListener(new ExecuteOnSetUnseenListener());
    }

    /**
     * Adds a listener to execute if an unread {@link Message} is found.
     */
    public void addOnUnreadMessageFoundListener(Consumer<MessageDataHolder> listener) {
        this.listeners.add(listener);
    }

    /**
     * Adds an additional {@link SearchTerm} to filter {@link Message messages} with.
     */
    public void addSearchTerm(SearchTerm searchTerm) {
        filter = new AndTerm(filter, searchTerm);
    }

    @Override
    protected void beforeListeners(IMAPFolder folder) {
        Message[] messages;
        try {
            messages = folder.search(filter);
        } catch (MessagingException e) {
            log.error("Failed to initially retrieve messages.", e);
            return;
        }
        Arrays.sort(messages, OLDEST_MESSAGE_FIRST_COMPARATOR);
        for (Message message : messages) {
            execute(message);
        }
    }

    private void execute(Message message) {
        if (log.isTraceEnabled()) {
            log.trace("Going to execute message {}", message.getMessageNumber());
        }

        MessageDataHolder messageDataHolder;
        try {
            messageDataHolder = MessageDataHolderBuilder.build(message);
        } catch (MessagingException e) {
            log.error("Failed to parse message {}" + message.getMessageNumber(), e);
            return;
        }

        try {
            listeners.forEach(listener -> listener.accept(messageDataHolder));
        } catch (RuntimeException e) {
            log.error("Exception while executing listeners of message " + message.getMessageNumber(), e);
            return;
        }

        try {
            message.setFlag(Flag.SEEN, true);
        } catch (MessagingException e) {
            log.error("Failed to set message " + message.getMessageNumber() + " to seen.", e);
        }
    }

    /**
     * {@link ExecuteUnreadMessagesReceiver#execute(Message[]) Executes} new {@link Message messages} whose {@link Flag#SEEN
     * "seen"-flag} is set to <code>false</code>.
     */
    private class ExecuteOnNewListener implements MessageCountListener {

        @Override
        public void messagesRemoved(MessageCountEvent event) {
            // Nothing to do
        }

        @Override
        public void messagesAdded(MessageCountEvent event) {
            Arrays.stream(event.getMessages())//
                    .filter(message -> filter.match(message))//
                    .sorted(OLDEST_MESSAGE_FIRST_COMPARATOR)//
                    .forEachOrdered(message -> execute(message));
        }
    }

    /**
     * {@link ExecuteUnreadMessagesReceiver#execute(Message[]) Executes} {@link Message messages} whose {@link Flag#SEEN
     * "seen"-flag} got set to <code>false</code>.
     */
    private class ExecuteOnSetUnseenListener implements MessageChangedListener {

        @Override
        public void messageChanged(MessageChangedEvent event) {
            if (event.getMessageChangeType() == MessageChangedEvent.FLAGS_CHANGED) {
                IMAPMessage message = (IMAPMessage) event.getMessage();
                if (filter.match(message)) {
                    execute(message);
                }
            }
        }
    }
}
