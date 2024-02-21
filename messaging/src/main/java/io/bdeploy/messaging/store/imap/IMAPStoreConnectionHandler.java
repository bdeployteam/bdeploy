package io.bdeploy.messaging.store.imap;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.angus.mail.imap.DefaultFolder;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPSSLStore;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.messaging.store.StoreConnectionHandler;
import jakarta.mail.Folder;
import jakarta.mail.FolderClosedException;
import jakarta.mail.MessagingException;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Session;
import jakarta.mail.URLName;

/**
 * Connects to an {@link IMAPFolder} and keeps it {@link IMAPFolder#idle() idleing} until {@link #close()} is called. If no
 * {@link URLName#getFile() folder} is provided in the {@link URLName} when calling {@link #connect(URLName)} the opened
 * {@link Folder} will be the {@link DefaultFolder}.
 */
public class IMAPStoreConnectionHandler extends StoreConnectionHandler<IMAPStore, IMAPFolder> {

    private static final Logger log = LoggerFactory.getLogger(IMAPStoreConnectionHandler.class);
    private static final Duration IDLE_TIME = Duration.ofMinutes(5);

    private final FolderOpeningStyle folderOpeningStyle;

    private Thread idleThread;

    /**
     * Creates a new {@link IMAPStoreConnectionHandler} with
     * {@link io.bdeploy.messaging.store.StoreConnectionHandler.FolderOpeningStyle#ReadOnly FolderOpeningStyle#ReadOnly}.
     */
    public IMAPStoreConnectionHandler() {
        this(FolderOpeningStyle.ReadOnly);
    }

    /**
     * Creates a new {@link IMAPStoreConnectionHandler}.
     *
     * @param folderOpeningStyle The {@link io.bdeploy.messaging.store.StoreConnectionHandler.FolderOpeningStyle
     *            FolderOpeningStyle} to open the {@link IMAPFolder} with
     */
    public IMAPStoreConnectionHandler(FolderOpeningStyle folderOpeningStyle) {
        this.folderOpeningStyle = folderOpeningStyle;
    }

    @Override
    public CompletableFuture<Void> connect(URLName url) {
        return super.connect(url).thenRun(() -> idle(url));
    }

    @Override
    public void disconnect() {
        if (idleThread != null && idleThread.isAlive()) {
            idleThread.interrupt();
        }
    }

    @Override
    protected Session createSession(Properties properties) throws NoSuchProviderException {
        String protocol = getProtocol();
        switch (protocol) {
            case "imap":
                properties.put("mail.imap.starttls.enable", "true");
                properties.put("mail.imap.peek", "true");
                return Session.getInstance(properties);
            case "imaps":
                properties.put("mail.imaps.peek", "true");
                return Session.getInstance(properties);
        }
        throw new NoSuchProviderException("Transport protocol " + protocol + " is not supported.");
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
        throw new NoSuchProviderException("Transport protocol " + protocol + " is not supported.");
    }

    @Override
    protected IMAPFolder createFolder(IMAPStore service, String folderName) throws MessagingException {
        return (IMAPFolder) service.getFolder(folderName);
    }

    @Override
    protected FolderOpeningStyle getFolderOpeningStyle() {
        return folderOpeningStyle;
    }

    private Void idle(URLName url) {
        idleThread = new Thread(() -> {
            IMAPFolder folder = getFolder();

            boolean supportsIdle = false;
            try {
                folder.idle();
                supportsIdle = true;
            } catch (FolderClosedException e) {
                return;
            } catch (MessagingException e) {
                supportsIdle = false;
            }

            try {
                if (supportsIdle) {
                    while (!Thread.interrupted()) {
                        if (!folder.isOpen()) {
                            try {
                                folder.open(folderOpeningStyle.value);
                            } catch (MessagingException e1) {
                                log.error("Failed to reopen folder.", e1);
                                try {
                                    connect(url).get();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    return;
                                } catch (ExecutionException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        folder.idle();
                        log.trace("idle done");
                    }
                } else {
                    while (!Thread.interrupted()) {
                        try {
                            Thread.sleep(IDLE_TIME);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        folder.getMessageCount();
                    }
                }
            } catch (MessagingException e) {
                throw new IllegalStateException("Exception while idleing", e);
            }
        });
        idleThread.start();
        return null;
    }
}
