package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.SortedSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.TestCliTool;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.interfaces.descriptor.product.ProductDescriptor;
import io.bdeploy.interfaces.descriptor.product.ProductVersionDescriptor;
import io.bdeploy.minion.cli.MinionServerCli;
import io.bdeploy.minion.cli.ProductTool;
import io.bdeploy.minion.cli.TemplateTool;
import io.bdeploy.pcu.TestAppFactory;

@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class TemplateCliTest {

    @RegisterExtension
    TestCliTool cli = new TestCliTool(new MinionServerCli());

    @Test
    void testGenAndReimport(@TempDir Path tmp, ActivityReporter reporter) throws IOException {
        Path templ = tmp.resolve("templ");
        Path hive = tmp.resolve("hive"); // don't use TestHive to allow access to path

        Path dummy = TestAppFactory.createDummyApp("dummy", tmp);
        Path jdk = TestAppFactory.createDummyAppNoDescriptor("jdk", tmp);

        Manifest.Key jdkKey = new Manifest.Key("jdk/" + OsHelper.getRunningOs().name().toLowerCase(), "1.8.0");

        // make sure dependency is already present! this is "short-circuiting" remote dependency download.
        try (BHive h = new BHive(hive.toUri(), reporter)) {
            h.execute(new ImportOperation().setManifest(jdkKey).setSourcePath(jdk));
        }

        Path pdesc = tmp.resolve("product-info.yaml");
        Path pvdesc = tmp.resolve("version-info.yaml");

        ProductDescriptor p = new ProductDescriptor();
        p.name = "Dummy Product";
        p.product = "dummy";
        p.applications.add("dummy");
        p.versionFile = "version-info.yaml";

        ProductVersionDescriptor pv = new ProductVersionDescriptor();
        pv.version = "1.0.0";
        pv.appInfo.put("dummy", Collections.singletonMap(OsHelper.getRunningOs(), dummy.toString()));

        try (OutputStream os = Files.newOutputStream(pdesc)) {
            os.write(StorageHelper.toRawYamlBytes(p));
        }
        try (OutputStream os = Files.newOutputStream(pvdesc)) {
            os.write(StorageHelper.toRawYamlBytes(pv));
        }

        cli.getTool(ProductTool.class, "--hive=" + hive.toString(), "--import=" + pdesc).run();

        cli.getTool(TemplateTool.class, "--hive=" + hive.toString(), "--template=" + templ.toString(),
                "--product=" + "dummy/product:1.0.0", "--create").run();

        assertTrue(Files.isRegularFile(templ.resolve("template.json")));
        assertTrue(Files.isRegularFile(templ.resolve("config/config.json")));

        cli.getTool(TemplateTool.class, "--hive=" + hive.toString(), "--template=" + templ.toString(), "--load").run();

        try (BHive h = new BHive(hive.toUri(), reporter)) {
            SortedSet<Key> mfs = h.execute(new ManifestListOperation());

            // app, product, jdk, instance root, instance fragment for master node.
            assertEquals(5, mfs.size());
        }
    }

}
