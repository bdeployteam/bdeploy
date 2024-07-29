package io.bdeploy.messaging.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.messaging.MessageDataHolder;
import io.bdeploy.messaging.MimeFile;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.ws.rs.core.MediaType;

/**
 * Builds a {@link MessageDataHolder} from a {@link Message}.
 *
 * @see #build(Message)
 */
public class MessageDataHolderBuilder {

    private static final Logger log = LoggerFactory.getLogger(MessageDataHolderBuilder.class);
    private static final MediaType MULTIPART_ALTERNATIVE = MediaType.valueOf("multipart/alternative");
    private static final MediaType MULTIPART_MIXED = MediaType.valueOf("multipart/mixed");

    private MessageDataHolderBuilder() {
    }

    /**
     * Creates a new instance of {@link MessageDataHolder}. This method performs all required operations on the given
     * {@link Message} so that once the method completes no further {@link Exception}-handling is required.
     *
     * @param message The message to convert into a {@link MessageDataHolder}
     * @throws MessagingException For a diverse range of failures
     */
    public static MessageDataHolder build(Message message) throws MessagingException {
        Object content;
        try {
            content = message.getContent();
        } catch (IOException e) {
            throw new MessagingException("Failed to retrieve content of message " + message.getMessageNumber(), e);
        }

        Address sender = message.getFrom()[0];
        List<Address> recipients = Arrays.asList(message.getAllRecipients());
        String subject = message.getSubject();
        MediaType mediaType = MediaType.valueOf(message.getContentType());

        if (MediaType.TEXT_PLAIN_TYPE.isCompatible(mediaType)) {
            return new MessageDataHolder(sender, recipients, subject, content.toString(), mediaTypeToString(mediaType));
        }

        if (MULTIPART_ALTERNATIVE.isCompatible(mediaType) || MULTIPART_MIXED.isCompatible(mediaType)) {
            List<MimeFile> attachments = new ArrayList<>();
            String text = processMultipart((Multipart) content, attachments);
            return new MessageDataHolder(sender, recipients, subject, text, MediaType.TEXT_PLAIN, attachments);
        }

        throw new MessagingException("Media type " + mediaTypeToString(mediaType) + " is not supported.");
    }

    private static String processMultipart(Multipart multipart, List<MimeFile> attachments) throws MessagingException {
        int count = multipart.getCount();
        if (count == 0) {
            throw new MessagingException("Multiparts with no body parts are not supported.");
        }

        if (MULTIPART_ALTERNATIVE.isCompatible(MediaType.valueOf(multipart.getContentType()))) {
            String textFromBodyPart = null;
            int i = count - 1;
            do {
                textFromBodyPart = getTextFromBodyPart(multipart.getBodyPart(i), attachments);
                i--;
            } while (textFromBodyPart == null && i > 0);
            return textFromBodyPart;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                try (InputStream inputStream = bodyPart.getInputStream()) {
                    attachments.add(new MimeFile(bodyPart.getFileName(), inputStream.readAllBytes(), bodyPart.getContentType()));
                } catch (IOException e) {
                    log.error("Failed to read data from body part. It will be skipped.", e);
                }
            } else {
                sb.append(getTextFromBodyPart(bodyPart, attachments));
            }
        }
        return sb.toString().trim();
    }

    private static String getTextFromBodyPart(BodyPart bodyPart, List<MimeFile> attachments) throws MessagingException {
        MediaType mediaType = MediaType.valueOf(bodyPart.getContentType());

        Object content;
        try {
            content = bodyPart.getContent();
        } catch (IOException e) {
            log.error("Failed to extract content of body part with media type: {}", mediaTypeToString(mediaType), e);
            return "";
        }

        if (MediaType.TEXT_PLAIN_TYPE.isCompatible(mediaType)) {
            return content.toString();
        }
        if (content instanceof Multipart multipart) {
            return processMultipart(multipart, attachments);
        }

        if (log.isWarnEnabled()) {
            log.warn("Unsupported media type: {}", mediaTypeToString(mediaType));
        }
        return "";
    }

    private static String mediaTypeToString(MediaType mediaType) {
        return mediaType.getType() + '/' + mediaType.getSubtype();
    }
}
