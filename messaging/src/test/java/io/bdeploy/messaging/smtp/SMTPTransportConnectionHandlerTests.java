package io.bdeploy.messaging.smtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.bdeploy.messaging.MessageDataHolder;
import io.bdeploy.messaging.MimeFile;
import io.bdeploy.messaging.transport.smtp.SMTPTransportConnectionHandler;
import jakarta.mail.URLName;
import jakarta.mail.internet.InternetAddress;
import jakarta.ws.rs.core.MediaType;

/**
 * Contains unit tests for {@link SMTPTransportConnectionHandler}.
 */
class SMTPTransportConnectionHandlerTests {

    // SMTP config

    private static final String HOST = "DUMMY";
    private static final int PORT_SMTP = -1;  // commonly 587
    private static final int PORT_SMTPS = -1; // commonly 465
    private static final String USERNAME = "DUMMY";
    private static final String PASSWORD = "DUMMY";
    private static final String SENDER_ADDRESS = "DUMMY";
    private static final String RECIPIENT_ADDRESS = "DUMMY";

    // Other constants

    private static final String SENDER_NAME = "BDeployTestSender";
    private static final String RECIPIENT_NAME = "BDeployTestRecipient";

    private static final MimeFile TEST_ATTACHMENT =//
            new MimeFile("TestAttachmentName.txt", "Test Attachment Content".getBytes(), MediaType.TEXT_PLAIN);

    private static final String PROTOCOL_SMTP = "smtp";
    private static final String PROTOCOL_SMTPS = "smtps";

    private static final URLName URL_SMTP = new URLName(PROTOCOL_SMTP, HOST, PORT_SMTP, null, USERNAME, PASSWORD);
    private static final URLName URL_SMTPS = new URLName(PROTOCOL_SMTPS, HOST, PORT_SMTPS, null, USERNAME, PASSWORD);

    @Test
    void testClosingClosedHandler() {
        try (SMTPTransportConnectionHandler messageSender = new SMTPTransportConnectionHandler()) {
        }
        try (SMTPTransportConnectionHandler messageSender = new SMTPTransportConnectionHandler()) {
            messageSender.disconnect();
        }
        try (SMTPTransportConnectionHandler messageSender = new SMTPTransportConnectionHandler()) {
            messageSender.close();
        }
    }

    @Test
    void testWithoutConnection() throws Exception {
        MessageDataHolder dataHolder = createAndSetupBuilder("Test - No Connection");
        assertEquals(1, dataHolder.getRecipients().size());

        try (SMTPTransportConnectionHandler messageSender = new SMTPTransportConnectionHandler()) {
            messageSender.setMaxMessageSize(0);
            messageSender.setMaxMessageSize(1);
            boolean invalidArgumentExceptionThrown = false;
            try {
                messageSender.setMaxMessageSize(-1);
            } catch (Exception invalidArgument) {
                invalidArgumentExceptionThrown = true;
            }
            assertTrue(invalidArgumentExceptionThrown);
            assertEquals(1, messageSender.getMaxMessageSize());
        }
    }

    @Test
    @Disabled("Disabled because a SMTP connection must be configured.")
    void testSmtpWithoutAttachment() throws Exception {
        MessageDataHolder dataHolder = createAndSetupBuilder("Test - SMTP Without Attachment");
        try (SMTPTransportConnectionHandler messageSender = new SMTPTransportConnectionHandler()) {
            messageSender.connect(URL_SMTP);
            messageSender.send(dataHolder);
        }
    }

    @Test
    @Disabled("Disabled because a SMTP connection must be configured.")
    void testSmtpWithAttachment() throws Exception {
        MessageDataHolder dataHolder = createAndSetupBuilder("Test - SMTP With Attachment", TEST_ATTACHMENT);
        try (SMTPTransportConnectionHandler messageSender = new SMTPTransportConnectionHandler()) {
            messageSender.connect(URL_SMTP);
            messageSender.send(dataHolder);
        }
    }

    @Test
    @Disabled("Disabled because a SMTPS connection must be configured.")
    void testSmtpsWithoutAttachment() throws Exception {
        MessageDataHolder dataHolder = createAndSetupBuilder("Test - SMTPS Without Attachment");
        try (SMTPTransportConnectionHandler messageSender = new SMTPTransportConnectionHandler()) {
            messageSender.connect(URL_SMTPS);
            messageSender.send(dataHolder);
        }
    }

    @Test
    @Disabled("Disabled because a SMTPS connection must be configured.")
    void testSmtpsWithAttachment() throws Exception {
        MessageDataHolder dataHolder = createAndSetupBuilder("Test - SMTPS With Attachment", TEST_ATTACHMENT);
        try (SMTPTransportConnectionHandler messageSender = new SMTPTransportConnectionHandler()) {
            messageSender.connect(URL_SMTPS);
            messageSender.send(dataHolder);
        }
    }

    private static MessageDataHolder createAndSetupBuilder(String subject, MimeFile... attachments)
            throws UnsupportedEncodingException {
        return new MessageDataHolder(new InternetAddress(SENDER_ADDRESS, SENDER_NAME),
                List.of(new InternetAddress(RECIPIENT_ADDRESS, RECIPIENT_NAME)), subject, "Test-Text", MediaType.TEXT_PLAIN,
                Arrays.asList(attachments));
    }
}
