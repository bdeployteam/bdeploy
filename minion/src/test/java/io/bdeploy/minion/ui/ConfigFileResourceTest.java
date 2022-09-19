package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto.FileStatusType;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.ConfigFileResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.dto.ConfigFileDto;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.InternalServerErrorException;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
class ConfigFileResourceTest {

    @Test
    void updates(BHive local, CommonRootResource master, InstanceGroupResource igr, RemoteService remote, @TempDir Path tmp)
            throws Exception {
        Manifest.Key instance = TestFactory.createApplicationsAndInstance(local, master, remote, tmp, true);
        InstanceManifest im = InstanceManifest.of(local, instance);

        InstanceResource ir = igr.getInstanceResource("demo");
        ConfigFileResource cfr = ir.getConfigResource(im.getConfiguration().id);

        List<ConfigFileDto> files = cfr.listConfigFiles(instance.getTag(), im.getConfiguration().product.getName(),
                im.getConfiguration().product.getTag());
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

        InstanceConfiguration cfg = ir.readVersion(im.getConfiguration().id, "1");
        ir.update(im.getConfiguration().id, new InstanceUpdateDto(new InstanceConfigurationDto(cfg, null), updates), null, "1");

        String updatedTag = Long.toString(Long.valueOf(instance.getTag()) + 1);

        assertEquals(2,
                cfr.listConfigFiles(updatedTag, im.getConfiguration().product.getName(), im.getConfiguration().product.getTag())
                        .size());
        assertEquals("NEW-FILE",
                new String(Base64.decodeBase64(cfr.loadConfigFile(updatedTag, "path/to/new.txt")), StandardCharsets.UTF_8));
        assertEquals("{ \"cfg\": \"new-value\" }\n",
                new String(Base64.decodeBase64(cfr.loadConfigFile(updatedTag, "myconfig.json")), StandardCharsets.UTF_8));

        FileStatusDto del1 = new FileStatusDto();
        del1.file = "myconfig.json";
        del1.type = FileStatusType.DELETE;

        cfg = ir.readVersion(im.getConfiguration().id, updatedTag);
        ir.update(im.getConfiguration().id,
                new InstanceUpdateDto(new InstanceConfigurationDto(cfg, null), Collections.singletonList(del1)), null,
                updatedTag);

        String updatedTag2 = Long.toString(Long.valueOf(updatedTag) + 1);
        List<ConfigFileDto> mixed = cfr.listConfigFiles(updatedTag2, im.getConfiguration().product.getName(),
                im.getConfiguration().product.getTag());
        assertEquals(2, mixed.size());
        assertNotNull(mixed.stream().filter(f -> f.instanceId == null && f.productId != null && f.path.equals("myconfig.json")));
        assertNotNull(
                mixed.stream().filter(f -> f.instanceId != null && f.productId == null && f.path.equals("path/to/new.txt")));

        assertThrows(ClientErrorException.class, () -> {
            // wrong tag.
            InstanceConfiguration cfg2 = ir.readVersion(im.getConfiguration().id, "1");
            ir.update(im.getConfiguration().id,
                    new InstanceUpdateDto(new InstanceConfigurationDto(cfg2, null), Collections.singletonList(del1)), null, "1");
        });

        assertThrows(InternalServerErrorException.class, () -> {
            // file does not exist.
            InstanceConfiguration cfg3 = ir.readVersion(im.getConfiguration().id, updatedTag2);
            ir.update(im.getConfiguration().id,
                    new InstanceUpdateDto(new InstanceConfigurationDto(cfg3, null), Collections.singletonList(del1)), null,
                    updatedTag2);
        });
    }

}
