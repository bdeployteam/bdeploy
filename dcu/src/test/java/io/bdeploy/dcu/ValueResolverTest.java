package io.bdeploy.dcu;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessGroupConfiguration;
import io.bdeploy.interfaces.variables.ApplicationParameterProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.interfaces.variables.ManifestRefPathProvider;
import io.bdeploy.interfaces.variables.VariableResolver;
import io.bdeploy.interfaces.variables.VariableResolver.SpecialVariablePrefix;

@ExtendWith(TempDirectory.class)
public class ValueResolverTest {

    @Test
    public void testValueProvider(@TempDir Path tmp) throws Exception {
        SortedMap<Path, Manifest.Key> mfs = new TreeMap<>();
        Manifest.Key keyA1 = new Manifest.Key("a", "v1");
        Manifest.Key keyA2 = new Manifest.Key("a", "v2");
        Manifest.Key keyB1 = new Manifest.Key("b", "v1");

        mfs.put(Paths.get("path/to/a"), keyA1);
        mfs.put(Paths.get("path/to/a-v2"), keyA2);
        mfs.put(Paths.get("path/to/b"), keyB1);

        Path fakeDeploy = tmp.resolve("fake");
        DeploymentPathProvider dpp = new DeploymentPathProvider(fakeDeploy, "fakeId");
        InstanceNodeConfiguration dc = new InstanceNodeConfiguration();
        dc.name = "Fake Deployment";
        dc.uuid = "fakeId";

        ApplicationConfiguration a1c = new ApplicationConfiguration();
        a1c.application = keyA1;
        a1c.name = "Application Number One";
        a1c.start = new CommandConfiguration();
        a1c.start.executable = "rel/to/launcher.sh";
        a1c.start.parameters.add(fakeParam("a1p1", "a1p1-value", "--param=a1p1-value"));
        a1c.start.parameters.add(fakeParam("a1p2", "{{V:a1p1}}", "--other", "{{V:a1p1}}"));
        a1c.start.parameters.add(fakeParam("a1p3", "{{M:a:v1}}", "--mref", "{{M:a:v1}}"));
        a1c.start.parameters.add(fakeParam("a1p4", "{{P:CONFIG}}/file.json", "--cfg={{P:CONFIG}}/file.json"));
        a1c.start.parameters
                .add(fakeParam("a1p5", "{{V:Application Number One:a1p4}}.bak", "--bak={{V:Application Number One:a1p4}}.bak"));

        dc.applications.add(a1c);

        VariableResolver resolver = new VariableResolver(dpp, new ManifestRefPathProvider(dpp, mfs),
                new ApplicationParameterProvider(dc), Collections.emptyList());

        // tests whether the use cases in the a1c start command work.
        ProcessGroupConfiguration dd = dc.renderDescriptor(resolver);

        // unqualified reference when more than one manifest with the name exists
        assertThrows(RuntimeException.class, () -> resolver.apply(SpecialVariablePrefix.MANIFEST_REFERENCE.format("a")));

        assertThat(resolver.apply(SpecialVariablePrefix.DEPLOYMENT_PATH.format("CONFIG")),
                is(dpp.get(SpecialDirectory.CONFIG).toString()));

        // resolver does not expand recursively - ParameterConfiguration does.
        assertThat(resolver.apply(SpecialVariablePrefix.PARAMETER_VALUE.format("Application Number One:a1p2")), is("{{V:a1p1}}"));

        // but the descriptor expand recursively.
        boolean found = false;
        for (String rendered : dd.applications.get(0).start) {
            if (rendered.startsWith("--bak=") && rendered.endsWith("config/file.json.bak")) {
                found = true;
            }
        }
        assertTrue(found, "Cannot find expected recursively expanded parameter");
    }

    private ParameterConfiguration fakeParam(String id, String value, String... preRendered) {
        ParameterConfiguration f = new ParameterConfiguration();

        f.uid = id;
        f.value = value;
        f.preRendered.addAll(Arrays.asList(preRendered));

        return f;
    }

    @Test
    void osConditional() {
        VariableResolver resolver = new VariableResolver(null, null, null, Collections.emptyList());

        OperatingSystem current = OsHelper.getRunningOs();
        OperatingSystem notCurrent = current == OperatingSystem.LINUX ? OperatingSystem.WINDOWS : OperatingSystem.LINUX;

        assertEquals("xx", ParameterConfiguration.process("x{{" + notCurrent.name() + ":value}}x", resolver));
        assertEquals("xvaluex", ParameterConfiguration.process("x{{" + current.name() + ":value}}x", resolver));
    }

}