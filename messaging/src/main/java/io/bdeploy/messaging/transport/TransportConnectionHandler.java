package io.bdeploy.messaging.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import jakarta.mail.SendFailedException;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

//TODO re-check security features like encryption

/**
 * Handles the opening and closing of a connection to a {@link Transport}, as well as basic logic associated with it.
 *
 * @param <T> The type of {@link Transport} that this handler handles
 */
public abstract class TransportConnectionHandler<T extends Transport>//
        extends ServiceConnectionHandler<T> implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(TransportConnectionHandler.class);

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
    public void send(MessageDataHolder dataHolder) throws SendFailedException, MessagingException {
        List<? extends Address> recipients = dataHolder.getRecipients();
        if (recipients.isEmpty()) {
            log.warn("Attempted to send message, but no recipients were found.");
            return;
        }

        String subject = dataHolder.getSubject();
        String text = dataHolder.getText();
        String textMimeType = dataHolder.getTextMimeType();
        List<MimeFile> attachments = dataHolder.getAttachments();

        Message message = createMessage();
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

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        int sizeInBytes = -1;
        try {
            message.writeTo(os);
            sizeInBytes = os.size();
        } catch (IOException e) {
            log.error("Failed to determine size of message " + message.getMessageNumber(), e);
            sizeInBytes = -1;
        }

        if (maxMessageSizeInBytes > 0 && sizeInBytes > maxMessageSizeInBytes) {
            throw new MessagingException("Message is too big. Determined size is " + sizeInBytes
                    + "B but currently set maximum size is " + maxMessageSizeInBytes + "B.");
        }

        getService().sendMessage(message, message.getAllRecipients());

        log.info("Sent message '" + subject + "' via " + getService().getURLName() + " to " + recipients.size()
                + " recipient(s).");
    }

    @Override
    protected void modifyProperties(Properties properties) {
        properties.put("mail.transport.protocol", getProtocol());
    }

    protected abstract Message createMessage();
}
