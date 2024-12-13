package io.bdeploy.minion.mail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.glassfish.hk2.api.messaging.MessageReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.op.CopyOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.managed.ControllingMaster;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.managed.ManagedMasters;
import io.bdeploy.interfaces.manifest.managed.ManagedMastersConfiguration;
import io.bdeploy.jersey.ws.change.ObjectChangeBroadcaster;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;
import io.bdeploy.jersey.ws.change.msg.ObjectEvent;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import io.bdeploy.messaging.MessageDataHolder;
import io.bdeploy.messaging.MimeFile;
import io.bdeploy.messaging.util.MessageDataHolderBuilder;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeHint;
import io.bdeploy.ui.dto.ObjectChangeType;
import jakarta.mail.Message;

/**
 * Handles mail received events
 */
public class MinionRootMailHandler {

    private static final Logger log = LoggerFactory.getLogger(MinionRootMailHandler.class);

    private final MinionRoot root;
    private final Path tempDir;
    private final BHiveRegistry registry;

    private final ObjectChangeBroadcaster changes;

    /**
     * Creates a new {@link MinionRootMailHandler}.
     */
    public MinionRootMailHandler(MinionRoot root, BHiveRegistry registry, ObjectChangeBroadcaster changes) {
        this.root = root;
        this.tempDir = root.getTempDir();
        this.registry = registry;
        this.changes = changes;
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
                handleAttachment(name, content);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Failed to handle attachment " + name, e);
            }
        }
    }

    private void handleAttachment(String name, byte[] content) {
        log.info("Received attachment {}", name);

        if (!name.toLowerCase().endsWith(MinionSignedAttachment.SIGNED_SUFFIX)) {
            throw new IllegalArgumentException("Only signed .zip files are supported. The attachment will be ignored.");
        }

        String[] data = AttachmentUtils.getAttachmentDataFromName(Path.of(name));
        String hiveName = data[0];

        BHive target = registry.get(hiveName);

        if (target == null) {
            throw new IllegalStateException("Instance Group " + hiveName + " is unknown");
        }

        String serverName = data[2];
        ManagedMasters managedMasters = new ManagedMasters(target);
        ManagedMastersConfiguration cfg = managedMasters.read();
        ManagedMasterDto managedMaster = cfg.getManagedMaster(serverName);

        if (managedMaster == null) {
            throw new IllegalArgumentException("Server " + serverName + " is unknown");
        }

        // decrypt and verify signature.
        var attachment = MinionSignedAttachment.getVerified(root, managedMaster.auth, content);

        String[] signedData = AttachmentUtils.getAttachmentDataFromName(Path.of(attachment.getName()));
        String signedHiveName = signedData[0];
        String signedInstanceId = signedData[1];
        String signedServerName = signedData[2];

        String instanceId = data[1];
        if (!hiveName.equals(signedHiveName) || !instanceId.equals(signedInstanceId) || !serverName.equals(signedServerName)) {
            // the name carries information, so we must make sure nobody tinkered with it.
            throw new IllegalArgumentException(
                    "Attachment name does not match signed name: " + name + " - " + attachment.getName());
        }

        // use the signed name for the file.
        Path path = tempDir.resolve(attachment.getName());

        try {
            Files.write(path, attachment.getData());
            try (BHive source = new BHive(path.toUri(), null, new ActivityReporter.Null())) {
                // copy *all* manifests and objects.
                source.execute(new CopyOperation().setDestinationHive(target).setPartialAllowed(false));

                // for all instance manifests we received, associate master
                source.execute(new ManifestListOperation().setManifestName(InstanceManifest.getRootName(instanceId)))
                        .forEach(r -> new ControllingMaster(target, r).associate(serverName));
            }

            // update last message received timestamp
            ManagedMasterDto mm = cfg.getManagedMaster(serverName);
            mm.lastMessageReceived = Instant.now();
            managedMasters.attach(mm, true);

            // send update through websocket.
            var igkey = new InstanceGroupManifest(target).getKey();
            var details = Map.of(ObjectChangeDetails.KEY_NAME.name(), igkey.getName(), ObjectChangeDetails.KEY_TAG.name(),
                    igkey.getTag(), ObjectChangeDetails.CHANGE_HINT.name(), ObjectChangeHint.SERVERS.name());
            var update = new ObjectChangeDto(ObjectChangeType.INSTANCE_GROUP.name(), new ObjectScope(hiveName),
                    ObjectEvent.CHANGED, details);

            changes.send(update);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create file for " + name, e);
        } finally {
            PathHelper.deleteIfExistsRetry(path);
        }
    }
}
