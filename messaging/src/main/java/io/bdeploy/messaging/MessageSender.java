package io.bdeploy.messaging;

import java.util.concurrent.CompletableFuture;

import jakarta.mail.Message;

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
     * <p>
     * Never throws exceptions.
     *
     * @param dataHolder The {@link MessageDataHolder} to use
     * @return A {@link CompletableFuture} of the sending process
     */
    CompletableFuture<Void> send(MessageDataHolder dataHolder);
}
