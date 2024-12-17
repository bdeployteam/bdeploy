package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.TestMinion.AuthPack;
import io.bdeploy.ui.cli.RemoteReportTool;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
class RemoteReportCliTest extends BaseMinionCliTest {

    @Test
    void testRemoteCli(BHive local, CommonRootResource common, RemoteService remote, @TempDir Path tmp, @AuthPack String auth)
            throws IOException {
        TestFactory.createApplicationsAndInstance(local, common, remote, tmp, true);

        StructuredOutput result;

        /* List all possible reports */
        result = tools.execute(RemoteReportTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--list");
        assertEquals(1, result.size());
        assertEquals("Products In Use", result.get(0).get("Name"));
        assertEquals("productsInUse", result.get(0).get("Type"));
        assertEquals("Shows where products are used, in which version, and for what purpose.", result.get(0).get("Description"));

        /* Parameters help for productsInUse */
        result = tools.execute(RemoteReportTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--report=productsInUse",
                "--paramHelp");
        assertEquals(5, result.size());
        assertEquals("instanceGroup=ARG", result.get(0).get("Argument"));
        assertEquals("instance group filter", result.get(0).get("Description"));
        assertEquals("product=ARG", result.get(1).get("Argument"));
        assertEquals("key part of product manifest", result.get(1).get("Description"));
        assertEquals("productVersion=ARG", result.get(2).get("Argument"));
        assertEquals("product version filter", result.get(2).get("Description"));
        assertEquals("regex", result.get(3).get("Argument"));
        assertEquals("flag marking that supplied productVersion parameter is regular expression",
                result.get(3).get("Description"));
        assertEquals("purpose=ARG", result.get(4).get("Argument"));
        assertEquals("instance purpose filter", result.get(4).get("Description"));

        /* productsInUse report result */
        result = tools.execute(RemoteReportTool.class, "--remote=" + remote.getUri(), "--token=" + auth,
                "--report=productsInUse");
        assertEquals(1, result.size());
        assertEquals("title", result.get(0).get("InstanceGroupTitle"));
        assertEquals("demo", result.get(0).get("InstanceGroupName"));
        assertEquals("For Unit Test", result.get(0).get("InstanceGroupDescription"));
        assertEquals("aaa-bbb-ccc", result.get(0).get("InstanceId"));
        assertEquals("DemoInstance", result.get(0).get("InstanceName"));
        assertEquals("Demo Product for Unit Test", result.get(0).get("ProductId"));
        assertEquals("Dummy Product", result.get(0).get("ProductName"));
        assertEquals("1.0.0.1234", result.get(0).get("ProductVersion"));
        assertEquals("", result.get(0).get("ActiveVersion"));
        assertEquals("TEST", result.get(0).get("Purpose"));
        assertEquals("", result.get(0).get("SystemName"));
        assertEquals("", result.get(0).get("ManagedServer"));
        assertEquals("", result.get(0).get("LastCommunication"));

        /* productsInUse report result with parameters */
        result = tools.execute(RemoteReportTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--report=productsInUse",
                "--params=instanceGroup=demo", "--params=product=customer/product", "--params=productVersion=1.*",
                "--params=regex", "--params=purpose=TEST");
        assertEquals(1, result.size());

        /* productsInUse report result with parameters not matching anything */
        result = tools.execute(RemoteReportTool.class, "--remote=" + remote.getUri(), "--token=" + auth, "--report=productsInUse",
                "--params=instanceGroup=demo", "--params=product=customer/product", "--params=productVersion=1.*",
                "--params=purpose=TEST");
        assertEquals(0, result.size());
    }
}
