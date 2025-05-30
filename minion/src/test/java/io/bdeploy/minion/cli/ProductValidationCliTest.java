package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ExecutableDescriptor;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.cli.RemoteProductValidationTool;

@ExtendWith(TestMinion.class)
class ProductValidationCliTest extends BaseMinionCliTest {

    @Test
    void testValidProduct(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);
        var product = TestProductFactory.generateProduct();
        Path productPath = createAndUploadProduct(remote, tmp);
        Path validationDescriptorPath = TestProductFactory.generateAndWriteValidationDescriptor(productPath, product);

        StructuredOutput output = remote(remote, RemoteProductValidationTool.class, "--descriptor=" + validationDescriptorPath);

        assertEquals("Success", output.get(0).get("message"));
    }

    @Test
    void testProductWithValidationIssues(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);
        var product = TestProductFactory.generateProduct();
        ApplicationDescriptor applicationDescriptor = product.applications.get("app-info.yaml");
        ApplicationTemplateDescriptor appTemplate = product.applicationTemplates.get("app-template.yaml");
        appTemplate.processControl = new HashMap<>();

        // missing stop command parameter
        applicationDescriptor.stopCommand = generateInvalidStopCommand();

        // generate invalid keepAlive
        applicationDescriptor.processControl.supportsKeepAlive = false;
        appTemplate.processControl.put("keepAlive", true);

        // generate invalid autoStart
        applicationDescriptor.processControl.supportsAutostart = false;
        appTemplate.processControl.put("autostart", true);

        Path productPath = createAndUploadProduct(remote, tmp, product);
        Path validationDescriptorPath = TestProductFactory.generateAndWriteValidationDescriptor(productPath, product);
        StructuredOutput output = remote(remote, RemoteProductValidationTool.class, "--descriptor=" + validationDescriptorPath);

        Set<String> warnings = extractBySeverity("WARNING", output);
        assertEquals(0, warnings.size());

        Set<String> errors = extractBySeverity("ERROR", output);
        assertTrue(errors.contains("Instance template 'Default Test Configuration'"
                + " has group 'Only Group' which uses template 'server-with-sleep' and sets its parameter 'keepAlive' to"
                + " enabled, but the descriptor of the application forbids it"));
        assertTrue(errors.contains("Instance template 'Default Test Configuration'"
                + " has group 'Only Group' which uses template 'server-with-sleep' and sets its parameter 'autostart' to"
                + " enabled, but the descriptor of the application forbids it"));
        assertTrue(errors.contains("Application template 'server-with-sleep' has"
                + " 'keepAlive' enabled, but the descriptor of the application forbids it"));
        assertTrue(errors.contains("Application template 'server-with-sleep' has"
                + " 'autostart' enabled, but the descriptor of the application forbids it"));
        assertTrue(errors.contains("Parameter 'wait.time' of application 'server-app' must have a default value"));
        assertEquals(7, errors.size());
    }

    private static ExecutableDescriptor generateInvalidStopCommand() {
        ExecutableDescriptor stopCommand = new ExecutableDescriptor();
        stopCommand.launcherPath = "{{WINDOWS:stop.bat}}{{LINUX:stop.sh}}{{LINUX_AARCH64:stop.sh}}";
        ParameterDescriptor stopParam = new ParameterDescriptor();
        stopParam.id = "wait.time";
        stopParam.name = "Wait Time";
        stopParam.longDescription = "A numeric parameter that controls how long to wait";
        stopParam.groupName = "Timeouts";
        stopParam.parameter = "--wait";
        stopParam.type = VariableDescriptor.VariableType.NUMERIC;
        stopParam.mandatory = true;
        stopCommand.parameters = List.of(stopParam);
        return stopCommand;
    }

    private static Set<String> extractBySeverity(String severity, StructuredOutput validationOutput) {
        return IntStream.range(0, validationOutput.size()).filter(i -> severity.equals(validationOutput.get(i).get("Severity")))
                .mapToObj(i -> validationOutput.get(i).get("Message")).collect(Collectors.toSet());
    }

}
