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
     * {@link io.bdeploy.messaging.store.StoreConnectionHandler.FolderOpeningMode#READ_ONLY FolderOpeningStyle#ReadOnly}.
     */
    public IMAPStoreConnectionHandler() {
        this(FolderOpeningMode.READ_ONLY);
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
        return switch (protocol) {
            case "imap" -> {
                properties.put("mail.imap.starttls.enable", "true");
                properties.put("mail.imap.peek", "true");
                properties.put("mail.imap.minidletime", "1000");
                yield Session.getInstance(properties);
            }
            case "imaps" -> {
                properties.put("mail.imaps.peek", "true");
                properties.put("mail.imaps.minidletime", "1000");
                yield Session.getInstance(properties);
            }
            default -> throw getNoSuchProviderException(protocol);
        };
    }

    @Override
    protected IMAPStore createService(URLName url) throws NoSuchProviderException {
        String protocol = getProtocol();
        return switch (protocol) {
            case "imap" -> new IMAPStore(getSession(), url);
            case "imaps" -> new IMAPSSLStore(getSession(), url);
            default -> throw getNoSuchProviderException(protocol);
        };
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
    protected void afterConnect(URLName url, boolean testMode) {
        super.afterConnect(url, testMode);
        if (!testMode) {
            idleThread = new Thread(this::idle, getClass().getSimpleName() + "IdleThread");
            idleThread.start();
        }
    }

    @Override
    public void disconnect() {
        if (idleThread != null && idleThread.isAlive()) {
            idleThread.interrupt();
            idleThread = null;
        }
        super.disconnect();
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
            log.error("Failed to send NOOP to {}", getFolderAndUrlLogString(), e);
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Sent NOOP to {}", getFolderAndUrlLogString());
        }
    }

    private void idle() {
        // Attempt idleing once
        boolean supportsIdle = false;
        try {
            if (log.isTraceEnabled()) {
                log.trace("Idleing on {}", getFolderAndUrlLogString());
            }
            getFolder().idle();
            supportsIdle = true;
        } catch (FolderClosedException | IllegalStateException e) {
            log.error("Folder was closed -> idle handling could not be started | {}", getFolderAndUrlLogString(), e);
            return;
        } catch (MessagingException e) {
            supportsIdle = false;
        }

        // Loop the appropriate idle logic
        try {
            if (supportsIdle) {
                doPreferredIdle();
            } else {
                doFallbackIdle();
            }
        } catch (MessagingException e) {
            log.error("Aborted idle handling due to unexpected exception |  {}", getFolderAndUrlLogString(), e);
        } catch (InterruptedException e) {
            log.info("Interrupted idle thread | {}", getFolderAndUrlLogString());
            Thread.currentThread().interrupt();
        }
    }

    private void doPreferredIdle() throws InterruptedException, MessagingException {
        IMAPFolder folder = getFolder();
        while (!Thread.interrupted()) {
            if (log.isTraceEnabled()) {
                log.trace("Idleing on {}", getFolderAndUrlLogString());
            }
            if (!folder.isOpen()) {
                log.info("Idle handling failed because folder is closed. Retrying in {} | {}", IDLE_RETRY_TIME,
                        getFolderAndUrlLogString());
                Thread.sleep(IDLE_RETRY_TIME);
                continue;
            }
            try {
                folder.idle();
            } catch (FolderClosedException | IllegalStateException e) {
                log.info("Idle handling failed. Retrying in {} | {}", IDLE_RETRY_TIME, getFolderAndUrlLogString(), e);
                Thread.sleep(IDLE_RETRY_TIME);
            }
        }
    }

    private void doFallbackIdle() throws InterruptedException, MessagingException {
        IMAPFolder folder = getFolder();
        while (!Thread.interrupted()) {
            if (log.isTraceEnabled()) {
                log.trace("Fallback idleing on {}", getFolderAndUrlLogString());
            }
            if (!folder.isOpen() || folder.getMessageCount() == -1) {
                log.info("Fallback idle handling failed because folder is closed. Retrying in {} | {}", IDLE_RETRY_TIME,
                        getFolderAndUrlLogString());
                Thread.sleep(IDLE_RETRY_TIME);
                continue;
            }
            Thread.sleep(FALLBACK_IDLE_FREQUENCY);
        }
    }
}
