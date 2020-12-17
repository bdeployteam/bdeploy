package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.InternalServerErrorException;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto.FileStatusType;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.ConfigFileResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.dto.ConfigFileDto;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TempDirectory.class)
public class ConfigFileResourceTest {

    @Test
    void updates(BHive local, CommonRootResource master, InstanceGroupResource igr, RemoteService remote, @TempDir Path tmp)
            throws Exception {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, master, remote, tmp, true);
        InstanceManifest im = InstanceManifest.of(local, instance);

        InstanceResource ir = igr.getInstanceResource("demo");
        ConfigFileResource cfr = ir.getConfigResource(im.getConfiguration().uuid);

        List<ConfigFileDto> files = cfr.listConfigFiles(instance.getTag());
        assertEquals(1, files.size());

        // see MinionDeployTest.createApplicationsAndInstance
        String cfgFile = cfr.loadConfigFile(instance.getTag(), "myconfig.json");
        assertEquals("{ \"cfg\": \"value\" }" + System.lineSeparator(),
                new String(Base64.decodeBase64(cfgFile), StandardCharsets.UTF_8));

        List<FileStatusDto> updates = new ArrayList<>();

        FileStatusDto upd1 = new FileStatusDto();
        upd1.content = Base64.encodeBase64String("{ \"cfg\": \"new-value\" }\n".getBytes());
        upd1.file = "myconfig.json";
        upd1.type = FileStatusType.EDIT;

        FileStatusDto upd2 = new FileStatusDto();
        upd2.content = Base64.encodeBase64String("NEW-FILE".getBytes());
        upd2.file = "path/to/new.txt";
        upd2.type = FileStatusType.ADD;

        updates.add(upd1);
        updates.add(upd2);

        cfr.updateConfigFiles(updates, instance.getTag());

        String updatedTag = Long.toString(Long.valueOf(instance.getTag()) + 1);

        assertEquals(2, cfr.listConfigFiles(updatedTag).size());
        assertEquals("NEW-FILE",
                new String(Base64.decodeBase64(cfr.loadConfigFile(updatedTag, "path/to/new.txt")), StandardCharsets.UTF_8));
        assertEquals("{ \"cfg\": \"new-value\" }\n",
                new String(Base64.decodeBase64(cfr.loadConfigFile(updatedTag, "myconfig.json")), StandardCharsets.UTF_8));

        FileStatusDto del1 = new FileStatusDto();
        del1.file = "myconfig.json";
        del1.type = FileStatusType.DELETE;

        cfr.updateConfigFiles(Collections.singletonList(del1), updatedTag);

        String updatedTag2 = Long.toString(Long.valueOf(updatedTag) + 1);
        assertEquals(1, cfr.listConfigFiles(updatedTag2).size());

        assertThrows(ClientErrorException.class, () -> {
            // wrong tag.
            cfr.updateConfigFiles(Collections.singletonList(del1), "1");
        });

        assertThrows(InternalServerErrorException.class, () -> {
            // file does not exist.
            cfr.updateConfigFiles(Collections.singletonList(del1), updatedTag2);
        });
    }

}
