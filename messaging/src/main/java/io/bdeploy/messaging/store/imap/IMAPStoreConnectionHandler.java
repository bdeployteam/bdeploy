package io.bdeploy.messaging.store.imap;

import java.time.Duration;
import java.util.Properties;

import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPSSLStore;
import org.eclipse.angus.mail.imap.IMAPStore;
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
    private static final Duration IDLE_TIME = Duration.ofMinutes(1);

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
        idle();
    }

    @Override
    public void disconnect() {
        if (idleThread != null && idleThread.isAlive()) {
            idleThread.interrupt();
        }
    }

    private void idle() {
        idleThread = new Thread(() -> {
            IMAPFolder folder = getFolder();

            // Make sure that the folder is open
            if (!folder.isOpen()) {
                try {
                    folder.open(getFolderOpeningMode().value);
                } catch (MessagingException e) {
                    log.error("Failed to open folder -> idle handling will be aborted | " + getFolderAndUrlLogString(), e);
                    return;
                }
            }

            // Attempt idleing once
            boolean supportsIdle = false;
            try {
                if (log.isTraceEnabled()) {
                    log.trace("Idleing on " + getFolderAndUrlLogString());
                }
                folder.idle();
                supportsIdle = true;
            } catch (FolderClosedException e) {
                throw new IllegalStateException("Unexpectedly closed " + getFolderAndUrlLogString(), e);
            } catch (MessagingException e) {
                supportsIdle = false;
            }

            // Loop the appropriate idle logic
            try {
                if (supportsIdle) {
                    // Preferred idle logic
                    while (!Thread.interrupted()) {
                        if (!folder.isOpen()) {
                            folder.open(getFolderOpeningMode().value);
                        }
                        if (log.isTraceEnabled()) {
                            log.trace("Idleing on " + getFolderAndUrlLogString());
                        }
                        folder.idle();
                    }
                } else {
                    // Fallback idle logic
                    while (!Thread.interrupted()) {
                        if (!folder.isOpen()) {
                            folder.open(getFolderOpeningMode().value);
                        }
                        if (log.isTraceEnabled()) {
                            log.trace("Fallback idleing on " + getFolderAndUrlLogString());
                        }
                        folder.getMessageCount();
                        Thread.sleep(IDLE_TIME);
                    }
                }
            } catch (MessagingException e) {
                log.error("Failed to idle " + getFolderAndUrlLogString(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }, getClass().getSimpleName() + "IdleThread");
        idleThread.start();
    }
}
