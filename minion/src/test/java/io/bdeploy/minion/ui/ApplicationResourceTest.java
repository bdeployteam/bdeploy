package io.bdeploy.minion.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.ApplicationResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.ProductResource;
import io.bdeploy.ui.dto.ApplicationDto;

@ExtendWith(TestMinion.class)
public class ApplicationResourceTest {

    @Test
    void applications(InstanceGroupResource root, RemoteService remote, @TempDir Path tmp) throws IOException {
        InstanceGroupConfiguration group = TestFactory.createInstanceGroup("Demo");
        root.create(group);

        ProductManifest product = TestFactory.pushProduct(group.name, remote, tmp);
        ProductResource productResource = root.getProductResource(group.name);
        ApplicationResource applicationResource = productResource.getApplicationResource(product.getKey().getName(),
                product.getKey().getTag());

        List<ApplicationDto> applications = applicationResource.list();
        assertEquals(1, applications.size());
        assertEquals(product.getApplications().first(), applications.get(0).key);
    }

}
