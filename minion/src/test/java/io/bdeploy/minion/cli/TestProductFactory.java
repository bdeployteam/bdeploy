package io.bdeploy.minion.cli;

import static io.bdeploy.common.util.OsHelper.OperatingSystem.LINUX;
import static io.bdeploy.common.util.OsHelper.OperatingSystem.LINUX_AARCH64;
import static io.bdeploy.common.util.OsHelper.OperatingSystem.WINDOWS;
import static io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType.INSTANCE;
import static io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType.MANUAL;
import static io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType.MANUAL_CONFIRM;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.product.v1.ProductVersionDescriptor;
import io.bdeploy.api.validation.v1.dto.ProductValidationDescriptorApi;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration.ProcessControlGroupHandlingType;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration.ProcessControlGroupWaitType;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor.ApplicationType;
import io.bdeploy.interfaces.descriptor.application.EndpointsDescriptor;
import io.bdeploy.interfaces.descriptor.application.ExecutableDescriptor;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint.HttpEndpointType;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor;
import io.bdeploy.interfaces.descriptor.instance.InstanceVariableDefinitionDescriptor;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateControlGroup;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateGroup;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
import io.bdeploy.interfaces.descriptor.template.SystemTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.SystemTemplateInstanceTemplateGroupMapping;
import io.bdeploy.interfaces.descriptor.template.TemplateApplication;
import io.bdeploy.interfaces.descriptor.template.TemplateParameter;
import io.bdeploy.interfaces.descriptor.template.TemplateVariable;
import io.bdeploy.interfaces.descriptor.template.TemplateVariableFixedValueOverride;
import io.bdeploy.interfaces.descriptor.template.TemplateVariableType;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor.VariableType;

public class TestProductFactory {

    private static final ObjectMapper YAML_MAPPER = JacksonHelper.getDefaultYamlObjectMapper();

    public static SystemTemplateDescriptor generateSystemTemplate() {
        SystemTemplateDescriptor system = new SystemTemplateDescriptor();
        system.name = "Test System";
        system.description = "SYSTEM_DESCRIPTION";

        InstanceTemplateReferenceDescriptor inst = new InstanceTemplateReferenceDescriptor();
        inst.name = "Test Instance";
        inst.description = "The Test Instance of the Test System";
        inst.productId = "io.bdeploy/test";
        inst.templateName = "Default Test Configuration";
        inst.fixedVariables = new ArrayList<>();

        SystemTemplateInstanceTemplateGroupMapping mapping = new SystemTemplateInstanceTemplateGroupMapping();
        mapping.group = "Only Group";
        mapping.node = "master";

        inst.defaultMappings = List.of(mapping);

        system.instances.add(inst);

        return system;
    }

    public static InstanceTemplateReferenceDescriptor generateInstanceTemplateReference() {
        return generateInstanceTemplateReference("Test Instance", "Default Test Configuration");
    }

    public static InstanceTemplateReferenceDescriptor generateInstanceTemplateReference(String instanceName,
            String templateName) {
        InstanceTemplateReferenceDescriptor inst = new InstanceTemplateReferenceDescriptor();
        inst.name = instanceName;
        inst.description = "Instance From TestProductFactory";
        inst.productId = "io.bdeploy/test";
        inst.templateName = templateName;
        inst.autoStart = true;
        inst.autoUninstall = false;
        inst.fixedVariables = new ArrayList<>();

        SystemTemplateInstanceTemplateGroupMapping mapping = new SystemTemplateInstanceTemplateGroupMapping();
        mapping.group = "Only Group";
        mapping.node = "master";

        inst.defaultMappings = List.of(mapping);

        return inst;
    }

    public static TestProductDescriptor generateProduct() {
        TestProductDescriptor product = new TestProductDescriptor();

        product.applications = new HashMap<>();
        product.applications.put("app-info.yaml", generateApplication());

        product.applicationTemplates = new HashMap<>();
        product.applicationTemplates.put("app-template.yaml", generateApplicationTemplate());
        product.applicationTemplates.put("app-template-2.yaml", generateApplicationTemplateWithFixedVariable());
        product.applicationTemplates.put("app-template-3.yaml", generateApplicationTemplateWithOtherVariable());

        product.instanceVariableDefinitions = new HashMap<>();
        product.instanceVariableDefinitions.put("instance-variable-definitions.yaml", generateInstanceVariableDefinitions());

        product.instanceTemplates = new HashMap<>();
        product.instanceTemplates.put("instance-template.yaml", generateInstanceTemplate());
        product.instanceTemplates.put("min-instance-template.yaml",
                TestProductFactory.generateMinimalInstanceTemplate("Small instance"));

        product.version = generateProductVersion("1.0.0");
        product.launchBat = "echo \"Successfully launched on WINDOWS\"";
        product.launchSh = "echo \"Successfully launched on LINUX\"";
        product.descriptor = generateProductInfo(product);

        return product;
    }

    private static ProductDescriptor generateProductInfo(TestProductDescriptor testDescriptor) {
        ProductDescriptor productDescriptor = new ProductDescriptor();
        productDescriptor.name = "Test Product";
        productDescriptor.product = "io.bdeploy/test";
        productDescriptor.vendor = "BDeploy Team";
        productDescriptor.applications = testDescriptor.version.appInfo.keySet().stream().toList();
        productDescriptor.applicationTemplates = testDescriptor.applicationTemplates.keySet().stream().toList();
        productDescriptor.instanceVariableDefinitions = testDescriptor.instanceVariableDefinitions.keySet().stream().toList();
        productDescriptor.instanceTemplates = testDescriptor.instanceTemplates.keySet().stream().toList();
        productDescriptor.versionFile = "product-version.yaml";
        productDescriptor.minMinionVersion = "1.2.3";
        return productDescriptor;
    }

    public static ProductVersionDescriptor generateProductVersion(String version) {
        ProductVersionDescriptor productVersion = new ProductVersionDescriptor();
        productVersion.version = version;
        productVersion.appInfo = Map.of("server-app",
                Map.of(WINDOWS, "app-info.yaml", LINUX, "app-info.yaml", LINUX_AARCH64, "app-info.yaml"));
        return productVersion;
    }

    public static InstanceTemplateDescriptor generateMinimalInstanceTemplate(String templateName) {
        InstanceTemplateGroup group = new InstanceTemplateGroup();
        group.name = "Only Group";
        group.applications = generateApplicationsForInstanceTemplate();

        InstanceTemplateControlGroup controlGroup = new InstanceTemplateControlGroup();
        controlGroup.name = "First Group";

        InstanceTemplateDescriptor tpl = new InstanceTemplateDescriptor();
        tpl.name = templateName;
        tpl.groups.add(group);
        tpl.processControlGroups.add(controlGroup);
        return tpl;
    }

    public static List<TemplateApplication> generateApplicationsForInstanceTemplate() {
        TemplateApplication appWithoutTemplate = generateTemplateApplication("Application 1");
        TemplateApplication appWithTemplateVariableWithDefault = generateTemplateApplication("Application 2");
        appWithTemplateVariableWithDefault.template = "server-with-sleep";
        TemplateApplication appWithTemplateVariableAndFixedValue = generateTemplateApplication("Application 3");
        appWithTemplateVariableAndFixedValue.template = "server-with-fixed-variable";
        TemplateApplication appWithAnotherTemplateVariableAndFixedValue = generateTemplateApplication("Application 4");
        appWithAnotherTemplateVariableAndFixedValue.template = "server-with-other-variable";
        return Arrays.asList(appWithoutTemplate, appWithTemplateVariableWithDefault, appWithTemplateVariableAndFixedValue,
                appWithAnotherTemplateVariableAndFixedValue);
    }

    public static TemplateApplication generateTemplateApplication(String appName) {
        TemplateApplication app1 = new TemplateApplication();
        app1.application = "server-app";
        app1.name = appName;
        app1.processControl = Map.of("startType", "MANUAL_CONFIRM");
        return app1;
    }

    public static InstanceTemplateDescriptor generateInstanceTemplate() {
        InstanceTemplateDescriptor tpl = new InstanceTemplateDescriptor();
        tpl.name = "Default Test Configuration";
        tpl.description = "Creates an instance with the default configuration";
        tpl.autoStart = true;
        tpl.autoUninstall = false;

        InstanceTemplateControlGroup pcg1 = new InstanceTemplateControlGroup();
        pcg1.name = "First Group";
        pcg1.startType = ProcessControlGroupHandlingType.PARALLEL;
        pcg1.startWait = ProcessControlGroupWaitType.WAIT;
        pcg1.stopType = null;
        InstanceTemplateControlGroup pcg2 = new InstanceTemplateControlGroup();
        pcg2.name = "Second Group";
        pcg2.startType = null;
        pcg2.startWait = null;
        pcg2.stopType = ProcessControlGroupHandlingType.PARALLEL;
        tpl.processControlGroups = Arrays.asList(pcg1, pcg2);

        InstanceTemplateGroup group = new InstanceTemplateGroup();
        group.name = "Only Group";
        group.description = "All server applications";
        TemplateApplication app1 = new TemplateApplication();
        app1.application = "server-app";
        app1.name = "Test Server Application";
        app1.description = "Test Application that prints a single line to the standard output";
        app1.processControl = Map.of("startType", "MANUAL_CONFIRM");
        TemplateApplication app2 = new TemplateApplication();
        app2.template = "server-with-sleep";
        group.applications = Arrays.asList(app1, app2);
        tpl.groups.add(group);
        return tpl;
    }

    public static InstanceVariableDefinitionDescriptor generateInstanceVariableDefinitions() {
        VariableDescriptor var1 = new VariableDescriptor();
        var1.id = "variable-definition-1";
        var1.name = "Instance variable definition 1";
        var1.longDescription = "An instance variable that was defined in an instance-variable-definiton.yaml (1)";

        VariableDescriptor var2 = new VariableDescriptor();
        var2.id = "variable-definition-2";
        var2.name = "Instance variable definition 2";
        var2.longDescription = "An instance variable that was defined in an instance-variable-definiton.yaml (2)";

        VariableDescriptor var3 = new VariableDescriptor();
        var3.id = "variable-definition-3";
        var3.name = "Instance variable definition 3";
        var3.longDescription = "An instance variable that was defined in an instance-variable-definiton.yaml (3)";

        InstanceVariableDefinitionDescriptor descr = new InstanceVariableDefinitionDescriptor();
        descr.definitions.add(var1);
        descr.definitions.add(var2);
        descr.definitions.add(var3);
        return descr;
    }

    private static ApplicationTemplateDescriptor generateApplicationTemplate() {
        ApplicationTemplateDescriptor tpl = new ApplicationTemplateDescriptor();
        tpl.id = "server-with-sleep";
        tpl.application = "server-app";
        tpl.name = "Server With Sleep";
        tpl.description = "A Server Application with the sleep parameter set to a given value.";
        tpl.processControl = null;
        tpl.applyOn = null;
        tpl.preferredProcessControlGroup = "First Group";

        TemplateVariable tplVar = new TemplateVariable();
        tplVar.id = "app-tpl-sleep";
        tplVar.name = "Sleep Timeout";
        tplVar.description = "The amount of time the server application should sleep";
        tplVar.type = TemplateVariableType.NUMERIC;
        tplVar.defaultValue = "750";
        tpl.fixedVariables = new ArrayList<>();
        tpl.templateVariables.add(tplVar);

        tpl.startParameters.add(aTemplateParameter("param.sleep", "{{T:app-tpl-sleep}}"));

        return tpl;
    }

    private static ApplicationTemplateDescriptor generateApplicationTemplateWithFixedVariable() {
        ApplicationTemplateDescriptor tpl = generateApplicationTemplate();
        tpl.id = "server-with-fixed-variable";
        tpl.name = "Server With Fixed Variable";
        tpl.description = "This Server Application defines a value for the template variable";
        tpl.fixedVariables.add(new TemplateVariableFixedValueOverride("app-tpl-sleep", "720"));
        return tpl;
    }

    private static ApplicationTemplateDescriptor generateApplicationTemplateWithOtherVariable() {
        ApplicationTemplateDescriptor tpl = new ApplicationTemplateDescriptor();
        tpl.id = "server-with-other-variable";
        tpl.application = "server-app";
        tpl.name = "Server With Other Variable";
        tpl.description = "This Server Application uses a different template variable.";
        tpl.fixedVariables.add(new TemplateVariableFixedValueOverride("app-tpl-sleep-2", "700"));
        tpl.processControl = null;
        tpl.applyOn = null;

        TemplateVariable tplVar = new TemplateVariable();
        tplVar.id = "app-tpl-sleep-2";
        tplVar.name = "Sleep Timeout";
        tplVar.description = "The amount of time the server application should sleep";
        tplVar.type = TemplateVariableType.NUMERIC;
        tplVar.defaultValue = "760";
        tpl.templateVariables.add(tplVar);

        tpl.startParameters.add(aTemplateParameter("param.sleep", "{{T:app-tpl-sleep-2}}"));

        return tpl;
    }

    private static ApplicationDescriptor generateApplication() {
        HttpEndpoint endpoint1 = new HttpEndpoint();
        HttpEndpoint endpoint2 = new HttpEndpoint();
        HttpEndpoint endpoint3 = new HttpEndpoint();
        endpoint1.id = "endpoint-1";
        endpoint2.id = "endpoint-2";
        endpoint3.id = "endpoint-3";
        endpoint1.path = new LinkedValueConfiguration("dummyPath1");
        endpoint2.path = new LinkedValueConfiguration("dummyPath2");
        endpoint3.path = new LinkedValueConfiguration("dummyPath3");
        endpoint1.type = HttpEndpointType.DEFAULT;
        endpoint2.type = HttpEndpointType.PROBE_STARTUP;
        endpoint3.type = HttpEndpointType.PROBE_ALIVE;

        EndpointsDescriptor endpoints = new EndpointsDescriptor();
        endpoints.http.add(endpoint1);
        endpoints.http.add(endpoint2);
        endpoints.http.add(endpoint3);

        ApplicationDescriptor app = new ApplicationDescriptor();
        app.name = "server-app";
        app.type = ApplicationType.SERVER;
        app.supportedOperatingSystems = List.of(WINDOWS, LINUX, LINUX_AARCH64);
        app.endpoints = endpoints;

        ProcessControlDescriptor processControl = new ProcessControlDescriptor();
        processControl.gracePeriod = 3000;
        processControl.supportedStartTypes = List.of(MANUAL, MANUAL_CONFIRM, INSTANCE);
        processControl.supportsKeepAlive = true;
        processControl.noOfRetries = 2;
        app.processControl = processControl;

        ExecutableDescriptor startCommand = new ExecutableDescriptor();
        startCommand.launcherPath = "{{WINDOWS:launch.bat}}{{LINUX:launch.sh}}{{LINUX_AARCH64:launch.sh}}";
        ParameterDescriptor param = new ParameterDescriptor();
        param.id = "param.sleep";
        param.name = "Sleep Timeout";
        param.longDescription = "A numeric parameter - controls how long the application stays alive";
        param.groupName = "Sleep Configuration";
        param.parameter = "--sleep";
        param.defaultValue = new LinkedValueConfiguration("10");
        param.type = VariableType.NUMERIC;
        param.mandatory = true;
        startCommand.parameters = new ArrayList<>(List.of(param));
        app.startCommand = startCommand;

        ExecutableDescriptor stopCommand = new ExecutableDescriptor();
        stopCommand.launcherPath = "{{WINDOWS:stop.bat}}{{LINUX:stop.sh}}{{LINUX_AARCH64:stop.sh}}";
        ParameterDescriptor stopParam = new ParameterDescriptor();
        stopParam.id = "wait.time";
        stopParam.name = "Wait Time";
        stopParam.longDescription = "A numeric parameter that controls how long to wait";
        stopParam.groupName = "Timeouts";
        stopParam.parameter = "--wait";
        stopParam.defaultValue = new LinkedValueConfiguration("25");
        stopParam.type = VariableType.NUMERIC;
        stopParam.mandatory = true;
        stopCommand.parameters = new ArrayList<>(List.of(param));
        app.stopCommand = stopCommand;

        return app;
    }

    public static Path generateAndWriteValidationDescriptor(Path productPath, TestProductDescriptor product) {
        ProductValidationDescriptorApi validationDescriptor = new ProductValidationDescriptorApi();
        validationDescriptor.product = "product-info.yaml";
        product.applications.keySet().forEach(appInfoPath -> {
            validationDescriptor.applications.put(product.applications.get(appInfoPath).name, appInfoPath);
        });

        try {
            Path validationDescriptorPath = productPath.resolve("validation-descriptor.yaml");
            Files.writeString(validationDescriptorPath, YAML_MAPPER.writeValueAsString(validationDescriptor));
            return validationDescriptorPath;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to write validation descriptor to file", e);
        }
    }

    public static void writeToFile(Path path, Object obj) {
        try {
            String text = YAML_MAPPER.writeValueAsString(obj);
            Files.writeString(path, text);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to write object to file", e);
        }
    }

    public static void writeProductToFile(Path productPath, TestProductDescriptor product) {
        try {
            Files.writeString(productPath.resolve("product-info.yaml"), YAML_MAPPER.writeValueAsString(product.descriptor));
            Files.writeString(productPath.resolve("product-version.yaml"), YAML_MAPPER.writeValueAsString(product.version));
            writeFiles(productPath, product.applications);
            writeFiles(productPath, product.applicationTemplates);
            writeFiles(productPath, product.instanceVariableDefinitions);
            writeFiles(productPath, product.instanceTemplates);
            Files.writeString(productPath.resolve("launch.bat"), product.launchBat);
            Files.writeString(productPath.resolve("launch.sh"), product.launchSh);

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to write product to file", e);
        }
    }

    private static void writeFiles(Path productPath, Map<String, ?> map) throws IOException {
        for (Map.Entry<String, ?> e : map.entrySet()) {
            System.out.println(e.getKey());
            System.out.println(YAML_MAPPER.writeValueAsString(e.getValue()));
            Path path = productPath.resolve(e.getKey());
            Files.writeString(path, YAML_MAPPER.writeValueAsString(e.getValue()));
        }
    }

    public static TemplateParameter aTemplateParameter(String id, String value) {
        TemplateParameter param = new TemplateParameter();
        param.id = id;
        param.value = value;
        return param;
    }

    public static TemplateVariable aTemplateVariable(String id, String defaultValue) {
        TemplateVariable templateVariable = new TemplateVariable();
        templateVariable.id = id;
        templateVariable.name = id;
        templateVariable.description = "Something that is related to " + id;
        templateVariable.defaultValue = defaultValue;
        templateVariable.type = TemplateVariableType.NUMERIC;
        return templateVariable;
    }

    public static class TestProductDescriptor {

        public ProductDescriptor descriptor;
        public ProductVersionDescriptor version;
        public Map<String, ApplicationDescriptor> applications;
        public Map<String, ApplicationTemplateDescriptor> applicationTemplates;
        public Map<String, InstanceVariableDefinitionDescriptor> instanceVariableDefinitions;
        public Map<String, InstanceTemplateDescriptor> instanceTemplates;
        public String launchBat;
        public String launchSh;

    }

}
