package io.bdeploy.messaging;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.event.FolderListener;
import jakarta.mail.event.MessageChangedListener;
import jakarta.mail.event.MessageCountListener;

/**
 * Interface for generic {@link Message} receivers.
 * <p>
 * Note that the listeners only take effect after {@link ConnectionHandler#connect(jakarta.mail.URLName) connect} is called.
 */
public interface MessageReceiver extends ConnectionHandler {

    /**
     * @param listener The {@link FolderListener} to add to the {@link Folder}
     */
    void addListener(FolderListener listener);

    /**
     * @param listener The {@link MessageChangedListener} to add to the {@link Folder}
     */
    void addListener(MessageChangedListener listener);

    /**
     * @param listener The {@link MessageCountListener} to add to the {@link Folder}
     */
    void addListener(MessageCountListener listener);
}
