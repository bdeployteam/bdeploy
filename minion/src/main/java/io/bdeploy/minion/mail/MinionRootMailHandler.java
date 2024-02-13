package io.bdeploy.minion.mail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.glassfish.hk2.api.messaging.MessageReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.messaging.MessageDataHolder;
import io.bdeploy.messaging.MimeFile;
import io.bdeploy.messaging.util.MessageDataHolderBuilder;
import jakarta.mail.Message;

/**
 * Handles mail received events
 */
public class MinionRootMailHandler {

    private static final Logger log = LoggerFactory.getLogger(MinionRootMailHandler.class);

    private final Path tempDir;
    private final BHiveRegistry registry;

    /**
     * Creates a new {@link MinionRootMailHandler}.
     */
    public MinionRootMailHandler(Path tempDir, BHiveRegistry registry) {
        this.tempDir = tempDir;
        this.registry = registry;
    }

    /**
     * This method is supposed to be used as a "on message received listener" for a {@link MessageReceiver}.
     *
     * @param dataHolder The {@link MessageDataHolderBuilder} that contains the parsed contents of the {@link Message}
     */
    public void handleMail(MessageDataHolder dataHolder) {
        for (MimeFile mimeFile : dataHolder.getAttachments()) {
            String name = mimeFile.getName();
            byte[] content = mimeFile.getContent();
            try {
                handleAttachment(tempDir, name, content);
            } catch (RuntimeException e) {
                log.warn("Failed to handle attachment " + name, e);
            }
        }
    }

    private void handleAttachment(Path tempDir, String name, byte[] content) {
        if (log.isTraceEnabled()) {
            log.trace("Handling attachment " + name);
        }

        Path path = tempDir.resolve(name);
        if (!path.toString().toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("Only .zip files are supported. The attachment will be ignored.");
        }

        String bHiveId = AttachmentUtils.getAttachmentDataFromName(path)[0];

        try {
            Files.write(path, content);
            try (BHive source = new BHive(path.toUri(), null, new ActivityReporter.Null());
                    BHive target = registry.get(bHiveId);) {
                if (target == null) {
                    throw new IllegalStateException("Target BHive " + bHiveId + " could not be found.");
                }
                source.execute(new CopyOperation().setDestinationHive(target).setPartialAllowed(false));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create file at " + path, e);
        } finally {
            PathHelper.deleteIfExistsRetry(path);
        }
    }
}
