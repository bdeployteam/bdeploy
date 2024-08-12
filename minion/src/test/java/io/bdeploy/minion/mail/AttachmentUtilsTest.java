package io.bdeploy.minion.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class AttachmentUtilsTest {

    private static final String SEPARATOR = "#";

    @Test
    void testAttachmentUtils() {
        String name = "testGroupName";
        String instanceId = "hfdt-dm4-2d6v";
        String managedServerName = "managedServerName";

        String attachmentName = AttachmentUtils.getAttachmentNameFromData(name, instanceId, managedServerName);
        assertEquals(name + SEPARATOR + instanceId + SEPARATOR + managedServerName, attachmentName);

        String[] data = AttachmentUtils.getAttachmentDataFromName(Path.of(attachmentName));
        assertEquals(3, data.length);
        assertEquals(name, data[0]);
        assertEquals(instanceId, data[1]);
        assertEquals(managedServerName, data[2]);
    }
}
