package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.DownloadService;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.ProductResource;

@ExtendWith(TempDirectory.class)
@ExtendWith(TestMinion.class)
public class ProductResourceTest {

    @Test
    void products(InstanceGroupResource root, RemoteService remote, @TempDir Path tmp) throws IOException {
        InstanceGroupConfiguration group = TestFactory.createInstanceGroup("Demo");
        root.create(group);

        Key prod = TestFactory.pushProduct(group.name, remote, tmp).getKey();
        ProductResource products = root.getProductResource(group.name);

        assertEquals(1, products.list().size());
        assertEquals(prod, products.list().get(0).key);

        products.delete(prod.getName(), prod.getTag());

        assertTrue(products.list().isEmpty());
    }

    @Test
    void createZip(MinionRoot minion, InstanceGroupResource root, RemoteService remote, DownloadService dlService,
            @TempDir Path tmp) throws IOException {
        InstanceGroupConfiguration group = TestFactory.createInstanceGroup("Demo");
        root.create(group);

        Key prod = TestFactory.pushProduct(group.name, remote, tmp).getKey();
        ProductResource productResource = root.getProductResource("Demo");
        String zipToken = productResource.createProductZipFile(prod.getName(), prod.getTag());
        Path zipFile = minion.getDownloadDir().resolve(zipToken);
        assertTrue(zipFile.toFile().isFile());

        Response response = dlService.download(zipToken);
        assertEquals(Response.Status.OK, response.getStatusInfo().toEnum());
    }

}
