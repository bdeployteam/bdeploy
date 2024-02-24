package io.bdeploy.messaging.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.bdeploy.messaging.MessageDataHolder;
import io.bdeploy.messaging.MessageSender;
import io.bdeploy.messaging.MimeFile;
import io.bdeploy.messaging.ServiceConnectionHandler;
import jakarta.activation.DataHandler;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Transport;
import jakarta.mail.URLName;
import jakarta.mail.event.TransportEvent;
import jakarta.mail.event.TransportListener;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

/**
 * Handles the opening and closing of a connection to a {@link Transport}, as well as basic logic associated with it.
 *
 * @param <T> The type of {@link Transport} that this handler handles
 */
public abstract class TransportConnectionHandler<T extends Transport>//
        extends ServiceConnectionHandler<T> implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(TransportConnectionHandler.class);

    private final ExecutorService sendExecutor = Executors.newFixedThreadPool(3,
            new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName() + "SendThread-%d").build());

    /**
     * Default maximum message size is equal to 50 megabytes (1024 * 1024 * 50 = 52428800)
     */
    private int maxMessageSizeInBytes = 52428800;

    /**
     * @throws IllegalArgumentException If the given maximum message size is < 0
     */
    @Override
    public void setMaxMessageSize(int maxMessageSizeInBytes) {
        if (maxMessageSizeInBytes < 0) {
            throw new IllegalArgumentException("Maximum message size cannot be negative.");
        }
        this.maxMessageSizeInBytes = maxMessageSizeInBytes;
    }

    @Override
    public int getMaxMessageSize() {
        return this.maxMessageSizeInBytes;
    }

    /**
     * @see Transport#sendMessage(Message, Address[])
     */
    @Override
    public CompletableFuture<Void> send(MessageDataHolder dataHolder) {
        return CompletableFuture.runAsync(() -> doSend(dataHolder), sendExecutor);
    }

    @Override
    public void close() {
        sendExecutor.close();
        super.close();
    }

    @Override
    protected void modifyProperties(Properties properties) {
        properties.put("mail.transport.protocol", getProtocol());
    }

    @Override
    protected void afterConnect(URLName url) {
        getService().addTransportListener(LoggingTransportListener.INSTANCE);
    }

    protected abstract Message createEmptyMessage();

    private void doSend(MessageDataHolder dataHolder) {
        try {
            List<? extends Address> recipients = dataHolder.getRecipients();
            if (recipients.isEmpty()) {
                log.warn("Attempted to send message, but no recipients were found.");
                return;
            }

            String subject = dataHolder.getSubject();
            String text = dataHolder.getText();
            String textMimeType = dataHolder.getTextMimeType();
            List<MimeFile> attachments = dataHolder.getAttachments();

            Message message = createEmptyMessage();
            try {
                message.setFrom(dataHolder.getSender());
                message.setRecipients(Message.RecipientType.TO, recipients.toArray(Address[]::new));
                message.setSubject(subject);

                if (attachments.isEmpty()) {
                    message.setContent(text, textMimeType);
                } else {
                    Multipart multipart = new MimeMultipart();

                    BodyPart textBodyPart = new MimeBodyPart();
                    textBodyPart.setContent(text, textMimeType);
                    multipart.addBodyPart(textBodyPart);

                    for (MimeFile attachment : attachments) {
                        BodyPart attachmentBodyPart = new MimeBodyPart();
                        attachmentBodyPart.setDataHandler(
                                new DataHandler(new ByteArrayDataSource(attachment.getContent(), attachment.getMimeType())));
                        attachmentBodyPart.setFileName(attachment.getName());
                        multipart.addBodyPart(attachmentBodyPart);
                    }

                    message.setContent(multipart);
                }

                if (maxMessageSizeInBytes > 0) {
                    int sizeInBytes;
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    try {
                        message.writeTo(os);
                        sizeInBytes = os.size();
                    } catch (MessagingException | IOException e) {
                        log.error("Failed to determine size of part.", e);
                        sizeInBytes = -1;
                    }
                    if (sizeInBytes > maxMessageSizeInBytes) {
                        throw new MessagingException("Message is too big. Determined size is " + sizeInBytes
                                + "B but currently set maximum size is " + maxMessageSizeInBytes + "B.");
                    }
                }

                log.info("Sending message '" + subject + "' via " + getService().getURLName() + " to " + recipients.size()
                        + " recipient(s).");

                reconnectIfNeeded();
                getService().sendMessage(message, message.getAllRecipients());
            } catch (MessagingException e) {
                log.error("Failed to send message.", e);
            }
        } catch (RuntimeException e) {
            log.error("Unexpected runtime exception during sending of message.", e);
        }
    }

    /**
     * A {@link TransportListener} which logs each {@link TransportEvent} at trace level.
     */
    private static class LoggingTransportListener implements TransportListener {

        private static LoggingTransportListener INSTANCE = new LoggingTransportListener();

        @Override
        public void messageDelivered(TransportEvent event) {
            if (!log.isTraceEnabled()) {
                return;
            }

            String source = event.getSource().toString();
            int type = event.getType();
            if (type != TransportEvent.MESSAGE_DELIVERED) {
                log.trace("Message delivered event handler called with unexpected event type. (Source="//
                        + source + ")(Type=" + type + ')');
                return;
            }
            log.trace("Message delivered from " + source + " to: " + buildEventInfo(event));
        }

        @Override
        public void messageNotDelivered(TransportEvent event) {
            if (!log.isTraceEnabled()) {
                return;
            }

            String source = event.getSource().toString();
            int type = event.getType();
            if (type != TransportEvent.MESSAGE_NOT_DELIVERED) {
                log.trace("Message not delivered event handler called with unexpected event type. (Source="//
                        + source + ")(Type=" + type + ')');
                return;
            }
            log.trace("Message not delivered from " + source + " to: " + buildEventInfo(event));
        }

        @Override
        public void messagePartiallyDelivered(TransportEvent event) {
            if (!log.isTraceEnabled()) {
                return;
            }

            String source = event.getSource().toString();
            int type = event.getType();
            if (type != TransportEvent.MESSAGE_PARTIALLY_DELIVERED) {
                log.trace("Message partially delivered event handler called with unexpected event type. (Source="//
                        + source + ")(Type=" + type + ')');
                return;
            }
            log.trace("Message partially delivered from " + source + " to: " + buildEventInfo(event));
        }

        private static String buildEventInfo(TransportEvent event) {
            Address[] validSentAddresses = event.getValidSentAddresses();
            Address[] validUnsentAddresses = event.getValidUnsentAddresses();
            Address[] invalidAddresses = event.getInvalidAddresses();

            StringBuilder sb = new StringBuilder();
            if (validSentAddresses != null && validSentAddresses.length != 0) {
                sb.append("Valid sent addresses: ");
                appendAddressInfo(sb, event.getValidSentAddresses());
            }
            if (validUnsentAddresses != null && validUnsentAddresses.length != 0) {
                sb.append(" Valid unsent addresses: ");
                appendAddressInfo(sb, event.getValidUnsentAddresses());
            }
            if (invalidAddresses != null && invalidAddresses.length != 0) {
                sb.append(" Invalid addresses: ");
                appendAddressInfo(sb, event.getInvalidAddresses());
            }
            return sb.toString();
        }

        private static void appendAddressInfo(StringBuilder sb, Address[] addresses) {
            for (Address address : addresses) {
                sb.append("[(Type=");
                sb.append(address.getType());
                sb.append(")(Value=");
                sb.append(address.toString());
                sb.append(")]");
            }
        }
    }
}
