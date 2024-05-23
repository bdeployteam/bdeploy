package io.bdeploy.messaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.mail.Address;
import jakarta.mail.Message;

/**
 * Holds data about a {@link Message}.
 */
public class MessageDataHolder {

    private final Address sender;
    private final List<Address> recipients;
    private final String subject;
    private final String text;
    private final String textMimeType;
    private final List<MimeFile> attachments;

    /**
     * Creates a new {@link MessageDataHolder} without any {@link MimeFile attachments}.
     */
    public MessageDataHolder(Address sender, List<Address> recipients, String subject, String text,//
            String textMimeType) {
        this(sender, recipients, subject, text, textMimeType, null);
    }

    /**
     * Creates a new {@link MessageDataHolder} including the given {@link MimeFile attachments}.
     */
    public MessageDataHolder(Address sender, List<Address> recipients, String subject, String text,//
            String textMimeType, List<MimeFile> attachments) {
        this.sender = sender;
        this.recipients = parseList(recipients);
        this.subject = subject;
        this.text = text;
        this.textMimeType = textMimeType;
        this.attachments = parseList(attachments);
    }

    private static <T> List<T> parseList(List<T> input) {
        return input == null ? new ArrayList<>() : Collections.unmodifiableList(input);
    }

    /**
     * @return The sender of the {@link Message}
     */
    public Address getSender() {
        return sender;
    }

    /**
     * @return An {@link Collections#unmodifiableList(List) unmodifiable} {@link List} of the recipients of the {@link Message}
     */
    public List<? extends Address> getRecipients() {
        return recipients;
    }

    /**
     * @return The subject of the {@link Message}
     */
    public String getSubject() {
        return subject;
    }

    /**
     * @return The text of the {@link Message}
     */
    public String getText() {
        return text;
    }

    /**
     * @return The mime type of the text of the {@link Message}
     */
    public String getTextMimeType() {
        return textMimeType;
    }

    /**
     * @return An {@link Collections#unmodifiableList(List) unmodifiable} {@link List} of the {@link MimeFile attachments} of the
     *         {@link Message}
     */
    public List<MimeFile> getAttachments() {
        return attachments;
    }
}
