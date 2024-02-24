package io.bdeploy.messaging.store.imap;

import java.time.Duration;
import java.util.Properties;

import org.eclipse.angus.mail.iap.ProtocolException;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPSSLStore;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.eclipse.angus.mail.imap.protocol.IMAPProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.messaging.store.StoreConnectionHandler;
import jakarta.mail.FolderClosedException;
import jakarta.mail.MessagingException;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Session;
import jakarta.mail.URLName;

/**
 * Connects to an {@link IMAPFolder} and keeps it {@link IMAPFolder#idle() idleing} until {@link #disconnect()} is called.
 */
public class IMAPStoreConnectionHandler extends StoreConnectionHandler<IMAPStore, IMAPFolder> {

    private static final Logger log = LoggerFactory.getLogger(IMAPStoreConnectionHandler.class);
    private static final Duration IDLE_RETRY_TIME = Duration.ofMinutes(1);
    private static final Duration FALLBACK_IDLE_FREQUENCY = Duration.ofMinutes(1);

    private final FolderOpeningMode folderOpeningMode;

    private Thread idleThread;

    /**
     * Creates a new {@link IMAPStoreConnectionHandler} with
     * {@link io.bdeploy.messaging.store.StoreConnectionHandler.FolderOpeningMode#ReadOnly FolderOpeningStyle#ReadOnly}.
     */
    public IMAPStoreConnectionHandler() {
        this(FolderOpeningMode.ReadOnly);
    }

    /**
     * Creates a new {@link IMAPStoreConnectionHandler}.
     *
     * @param folderOpeningStyle The {@link io.bdeploy.messaging.store.StoreConnectionHandler.FolderOpeningMode
     *            FolderOpeningStyle} to open the {@link IMAPFolder} with
     */
    public IMAPStoreConnectionHandler(FolderOpeningMode folderOpeningStyle) {
        this.folderOpeningMode = folderOpeningStyle;
    }

    @Override
    protected Session createSession(Properties properties) throws NoSuchProviderException {
        String protocol = getProtocol();
        switch (protocol) {
            case "imap":
                properties.put("mail.imap.starttls.enable", "true");
                properties.put("mail.imap.peek", "true");
                properties.put("mail.imap.minidletime", "1000");
                return Session.getInstance(properties);
            case "imaps":
                properties.put("mail.imaps.peek", "true");
                properties.put("mail.imaps.minidletime", "1000");
                return Session.getInstance(properties);
        }
        throw getNoSuchProviderException(protocol);
    }

    @Override
    protected IMAPStore createService(URLName url) throws NoSuchProviderException {
        String protocol = getProtocol();
        switch (protocol) {
            case "imap":
                return new IMAPStore(getSession(), url);
            case "imaps":
                return new IMAPSSLStore(getSession(), url);
        }
        throw getNoSuchProviderException(protocol);
    }

    @Override
    protected IMAPFolder createFolder(IMAPStore service, String folderName) throws MessagingException {
        return (IMAPFolder) service.getFolder(folderName);
    }

    @Override
    protected FolderOpeningMode getFolderOpeningMode() {
        return folderOpeningMode;
    }

    @Override
    protected void afterConnect(URLName url) {
        super.afterConnect(url);
        idleThread = new Thread(() -> idle(), getClass().getSimpleName() + "IdleThread");
        idleThread.start();
    }

    @Override
    public void disconnect() {
        if (idleThread != null && idleThread.isAlive()) {
            idleThread.interrupt();
            idleThread = null;
        }
    }

    @Override
    protected void keepAlive() {
        super.keepAlive();

        try {
            getFolder().doCommand(new IMAPFolder.ProtocolCommand() {

                @Override
                public Object doCommand(IMAPProtocol protocol) throws ProtocolException {
                    protocol.simpleCommand("NOOP", null);
                    return null;
                }
            });
        } catch (MessagingException e) {
            log.error("Failed to send NOOP to " + getFolderAndUrlLogString(), e);
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Sent NOOP to " + getFolderAndUrlLogString());
        }
    }

    private void idle() {
        IMAPFolder folder = getFolder();

        // Attempt idleing once
        boolean supportsIdle = false;
        try {
            if (log.isTraceEnabled()) {
                log.trace("Idleing on " + getFolderAndUrlLogString());
            }
            folder.idle();
            supportsIdle = true;
        } catch (FolderClosedException | IllegalStateException e) {
            log.error("Folder was closed -> idle handling could not be started | " + getFolderAndUrlLogString(), e);
            return;
        } catch (MessagingException e) {
            supportsIdle = false;
        }

        // Loop the appropriate idle logic
        try {
            if (supportsIdle) {
                // Preferred idle logic
                while (!Thread.interrupted()) {
                    if (log.isTraceEnabled()) {
                        log.trace("Idleing on " + getFolderAndUrlLogString());
                    }
                    if (!folder.isOpen()) {
                        log.info("Idle handling failed because folder is closed. Retrying in " + IDLE_RETRY_TIME + " | "
                                + getFolderAndUrlLogString());
                        Thread.sleep(IDLE_RETRY_TIME);
                        continue;
                    }
                    try {
                        folder.idle();
                    } catch (FolderClosedException | IllegalStateException e) {
                        log.info("Idle handling failed because folder is closed. Retrying in " + IDLE_RETRY_TIME + " | "
                                + getFolderAndUrlLogString());
                        Thread.sleep(IDLE_RETRY_TIME);
                    }
                }
            } else {
                // Fallback idle logic
                while (!Thread.interrupted()) {
                    if (log.isTraceEnabled()) {
                        log.trace("Fallback idleing on " + getFolderAndUrlLogString());
                    }
                    if (!folder.isOpen()) {
                        log.info("Fallback idle handling failed because folder is closed. Retrying in " + IDLE_RETRY_TIME + " | "
                                + getFolderAndUrlLogString());
                        Thread.sleep(IDLE_RETRY_TIME);
                        continue;
                    }
                    if (folder.getMessageCount() == -1) {
                        log.info("Fallback idle handling failed because folder is closed. Retrying in " + IDLE_RETRY_TIME + " | "
                                + getFolderAndUrlLogString());
                        Thread.sleep(IDLE_RETRY_TIME);
                        continue;
                    }
                    Thread.sleep(FALLBACK_IDLE_FREQUENCY);
                }
            }
        } catch (MessagingException e) {
            log.error("Aborted idle handling due to unexpected exception |  " + getFolderAndUrlLogString(), e);
        } catch (InterruptedException e) {
            log.info("Interrupted idle thread | " + getFolderAndUrlLogString());
            Thread.currentThread().interrupt();
        }
    }
}
