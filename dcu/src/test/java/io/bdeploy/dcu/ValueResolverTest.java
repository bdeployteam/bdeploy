package io.bdeploy.dcu;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.variables.ApplicationParameterProvider;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.interfaces.variables.DeploymentPathResolver;
import io.bdeploy.interfaces.variables.ManifestRefPathProvider;
import io.bdeploy.interfaces.variables.ManifestVariableResolver;
import io.bdeploy.interfaces.variables.OsVariableResolver;
import io.bdeploy.interfaces.variables.ParameterValueResolver;
import io.bdeploy.interfaces.variables.Resolvers;
import io.bdeploy.interfaces.variables.Variables;

class ValueResolverTest {

    @Test
    void testValueProvider(@TempDir Path tmp) {
        Manifest.Key keyA1 = new Manifest.Key("a", "v1");
        Manifest.Key keyA2 = new Manifest.Key("a", "v2");
        Manifest.Key keyB1 = new Manifest.Key("b", "v1");

        Map<Manifest.Key, Path> mfs = new TreeMap<>();
        mfs.put(keyA1, Paths.get("path/to/a"));
        mfs.put(keyA2, Paths.get("path/to/a-v2"));
        mfs.put(keyB1, Paths.get("path/to/b"));

        InstanceNodeConfiguration dc = new InstanceNodeConfiguration();
        dc.name = "Fake Deployment";
        dc.id = "fakeId";

        DeploymentPathProvider dpp = new DeploymentPathProvider(tmp.resolve("fakeDeploy"), tmp.resolve("fakeLogData"), dc.id,
                "1");

        ApplicationConfiguration a1c = new ApplicationConfiguration();
        a1c.application = keyA1;
        a1c.name = "Application Number One";
        a1c.id = "fakeId";
        a1c.start = new CommandConfiguration();
        a1c.start.executable = "rel/to/launcher.sh";
        a1c.start.parameters.add(fakeParam("a1p1", "a1p1-value", "--param=a1p1-value"));
        a1c.start.parameters.add(fakeParam("a1p2", "{{V:a1p1}}", "--other", "{{V:a1p1}}"));
        a1c.start.parameters.add(fakeParam("a1p3", "{{M:a:v1}}", "--mref", "{{M:a:v1}}"));
        a1c.start.parameters.add(fakeParam("a1p4", "{{P:CONFIG}}/file.json", "--cfg={{P:CONFIG}}/file.json"));
        a1c.start.parameters
                .add(fakeParam("a1p5", "{{V:Application Number One:a1p4}}.bak", "--bak={{V:Application Number One:a1p4}}.bak"));

        dc.applications.add(a1c);

        CompositeResolver list = Resolvers.forInstance(dc, "x", dpp);
        list.add(new ManifestVariableResolver(new ManifestRefPathProvider(mfs)));

        // tests whether the use cases in the a1c start command work.
        ProcessGroupConfiguration dd = dc.renderDescriptor(list, dc);

        // unqualified reference when more than one manifest with the name exists
        assertThrows(RuntimeException.class, () -> list.apply(Variables.MANIFEST_REFERENCE.format("a")));

        assertThat(list.apply(Variables.DEPLOYMENT_PATH.format("CONFIG")), is(dpp.get(SpecialDirectory.CONFIG).toString()));

        // resolver does not expand recursively - ParameterConfiguration does.
        assertThat(list.apply(Variables.PARAMETER_VALUE.format("Application Number One:a1p2")),
                is("{{V:Application Number One:a1p1}}"));

        // but the descriptor expand recursively.
        boolean found = false;
        for (String rendered : dd.applications.get(0).start) {
            if (rendered.startsWith("--bak=") && rendered.replace("\\", "/").endsWith("config/file.json.bak")) {
                found = true;
            }
        }
        assertTrue(found, "Cannot find expected recursively expanded parameter");
    }

    private static ParameterConfiguration fakeParam(String id, String value, String... preRendered) {
        ParameterConfiguration f = new ParameterConfiguration();

        f.id = id;
        f.value = new LinkedValueConfiguration(value);
        f.preRendered.addAll(Arrays.asList(preRendered));

        return f;
    }

    @Test
    void testOperatingSystemConditional() {
        OsVariableResolver resolver = new OsVariableResolver();

        OperatingSystem current = OsHelper.getRunningOs();
        OperatingSystem notCurrent = current == OperatingSystem.LINUX ? OperatingSystem.WINDOWS : OperatingSystem.LINUX;

        assertEquals("xx", TemplateHelper.process("x{{" + notCurrent.name() + ":value}}x", resolver));
        assertEquals("xvaluex", TemplateHelper.process("x{{" + current.name() + ":value}}x", resolver));
    }

}
