package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.DownloadService;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.api.SoftwareRepositoryResource;
import jakarta.ws.rs.core.Response;

@ExtendWith(TestMinion.class)
class ProductResourceTest {

    @Test
    void products(InstanceGroupResource root, RemoteService remote, @TempDir Path tmp) throws IOException {
        InstanceGroupConfiguration group = TestFactory.createInstanceGroup("Demo");
        root.create(group);

        Key prod = TestFactory.pushProduct(group.name, remote, tmp).getKey();
        ProductResource products = root.getProductResource(group.name);

        assertEquals(1, products.list(null).size());
        assertEquals(prod, products.list(null).get(0).key);

        products.delete(prod.getName(), prod.getTag());

        assertTrue(products.list(null).isEmpty());
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

    @Test
    void copyProduct(InstanceGroupResource root, SoftwareRepositoryResource repos, RemoteService remote, @TempDir Path tmp)
            throws IOException {
        InstanceGroupConfiguration group = TestFactory.createInstanceGroup("Demo");
        root.create(group);

        SoftwareRepositoryConfiguration repo = TestFactory.createSoftwareRepository("Repo");
        repos.create(repo);

        Key prod = TestFactory.pushProduct(repo.name, remote, tmp).getKey();

        ProductResource productResource = root.getProductResource("Demo");
        productResource.copyProduct("Repo", prod.getName(), List.of(prod.getTag()));

        assertEquals(1, productResource.list(null).size());
        assertEquals(prod, productResource.list(null).get(0).key);
    }

}
