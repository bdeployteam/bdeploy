package io.bdeploy.messaging;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;

/**
 * Interface for generic {@link Message} senders.
 */
public interface MessageSender extends ConnectionHandler {

    /**
     * Sets a maximum size for messages in bytes. Zero means "no limit".
     *
     * @param maxMessageSize Maximum size of a message in bytes, must be >= 0
     */
    void setMaxMessageSize(int maxMessageSize);

    /**
     * @return The maximum message size
     */
    int getMaxMessageSize();

    /**
     * Sends a {@link Message} based on the given {@link MessageDataHolder}.
     *
     * @param dataHolder The {@link MessageDataHolder} to use
     * @throws SendFailedException If one or more of the recipient addresses were invalid
     * @throws MessagingException For other failures
     */
    void send(MessageDataHolder dataHolder) throws SendFailedException, MessagingException;
}
