package io.bdeploy.minion.mail;

import java.util.stream.Collectors;

import org.apache.logging.log4j.util.Strings;
import org.glassfish.hk2.api.messaging.MessageReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.messaging.MessageDataHolder;
import io.bdeploy.messaging.MimeFile;
import io.bdeploy.messaging.util.MessageDataHolderBuilder;
import io.bdeploy.minion.MinionRoot;
import jakarta.mail.Message;

/**
 * Handles mail received events
 */
public class MinionRootMailHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MinionRootMailHandler.class);

    private final MinionRoot root;

    /**
     * Creates a new {@link MinionRootMailHandler} for the given {@link MinionRoot}.
     *
     * @param root The {@link MinionRoot} to create a {@link MinionRootMailHandler} for
     */
    public MinionRootMailHandler(MinionRoot root) {
        this.root = root;
    }

    /**
     * This method is supposed to be used as a "on message received listener" for a {@link MessageReceiver}.
     *
     * @param dataHolder The {@link MessageDataHolderBuilder} that contains the parsed contents of the {@link Message}
     */
    public void handleMail(MessageDataHolder dataHolder) {
        //TODO Replace this logging with a proper implementation
        LOG.info("Subject: " + dataHolder.getSubject());
        LOG.info("Text:\n" + dataHolder.getText());
        LOG.info("Attachments: "
                + Strings.join(dataHolder.getAttachments().stream().map(MimeFile::getName).collect(Collectors.toList()), ';'));

    }
}
