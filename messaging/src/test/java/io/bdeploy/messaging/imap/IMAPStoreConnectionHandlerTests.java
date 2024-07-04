package io.bdeploy.messaging.imap;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.bdeploy.messaging.store.StoreConnectionHandler.FolderOpeningMode;
import io.bdeploy.messaging.store.imap.IMAPStoreConnectionHandler;
import jakarta.mail.URLName;

/**
 * Contains unit tests for {@link IMAPStoreConnectionHandler}.
 */
class IMAPStoreConnectionHandlerTests {

    // IMAP config

    private static final String HOST = "DUMMY";
    private static final int PORT_IMAP = -1;  // commonly 143
    private static final int PORT_IMAPS = -1; // commonly 993
    private static final String USERNAME = "DUMMY";
    private static final String PASSWORD = "DUMMY";

    // Other constants

    private static final String PROTOCOL_IMAP = "imap";
    private static final String PROTOCOL_IMAPS = "imaps";
    private static final String FOLDER_INBOX = "inbox";

    private static final URLName URL_IMAP = new URLName(PROTOCOL_IMAP, HOST, PORT_IMAP, FOLDER_INBOX, USERNAME, PASSWORD);
    private static final URLName URL_IMAP_NOFOLDER = new URLName(PROTOCOL_IMAP, HOST, PORT_IMAP, null, USERNAME, PASSWORD);
    private static final URLName URL_IMAPS = new URLName(PROTOCOL_IMAPS, HOST, PORT_IMAPS, FOLDER_INBOX, USERNAME, PASSWORD);
    private static final URLName URL_IMAPS_NOFOLDER = new URLName(PROTOCOL_IMAPS, HOST, PORT_IMAPS, null, USERNAME, PASSWORD);

    @Test
    void testClosingClosedHandler() {
        try (IMAPStoreConnectionHandler messageReceiver = new IMAPStoreConnectionHandler()) {
            // Do nothing - just test the closing
        }
        try (IMAPStoreConnectionHandler messageReceiver = new IMAPStoreConnectionHandler()) {
            messageReceiver.disconnect();
        }
        try (IMAPStoreConnectionHandler messageReceiver = new IMAPStoreConnectionHandler()) {
            messageReceiver.close();
        }
        try (IMAPStoreConnectionHandler messageReceiver = new IMAPStoreConnectionHandler(FolderOpeningMode.READ_WRITE)) {
            // Do nothing - just test the closing
        }
        try (IMAPStoreConnectionHandler messageReceiver = new IMAPStoreConnectionHandler(FolderOpeningMode.READ_WRITE)) {
            messageReceiver.disconnect();
        }
        try (IMAPStoreConnectionHandler messageReceiver = new IMAPStoreConnectionHandler(FolderOpeningMode.READ_WRITE)) {
            messageReceiver.close();
        }
    }

    @Test
    @Disabled("Disabled because a IMAP folder must be configured.")
    void testImap() {
        try (IMAPStoreConnectionHandler messageReceiver = new IMAPStoreConnectionHandler(FolderOpeningMode.READ_ONLY)) {
            messageReceiver.connect(URL_IMAP);
            messageReceiver.connect(URL_IMAP_NOFOLDER);
        }
    }

    @Test
    @Disabled("Disabled because a IMAP folder must be configured.")
    void testImaps() {
        try (IMAPStoreConnectionHandler messageReceiver = new IMAPStoreConnectionHandler(FolderOpeningMode.READ_ONLY)) {
            messageReceiver.connect(URL_IMAPS);
            messageReceiver.connect(URL_IMAPS_NOFOLDER);
        }
    }
}
