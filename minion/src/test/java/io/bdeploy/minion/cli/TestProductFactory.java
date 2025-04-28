package io.bdeploy.minion.cli;

import static io.bdeploy.common.util.OsHelper.OperatingSystem.LINUX;
import static io.bdeploy.common.util.OsHelper.OperatingSystem.WINDOWS;
import static io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType.INSTANCE;
import static io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType.MANUAL;
import static io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType.MANUAL_CONFIRM;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.product.v1.ProductVersionDescriptor;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration.ProcessControlGroupHandlingType;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration.ProcessControlGroupWaitType;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ExecutableDescriptor;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor;
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
import io.bdeploy.interfaces.descriptor.template.TemplateVariableType;
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

        SystemTemplateInstanceTemplateGroupMapping mapping = new SystemTemplateInstanceTemplateGroupMapping();
        mapping.group = "Only Group";
        mapping.node = "master";

        inst.defaultMappings = List.of(mapping);

        system.instances = List.of(inst);

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

        SystemTemplateInstanceTemplateGroupMapping mapping = new SystemTemplateInstanceTemplateGroupMapping();
        mapping.group = "Only Group";
        mapping.node = "master";

        inst.defaultMappings = List.of(mapping);

        return inst;
    }

    public static TestProductDescriptor generateProduct() {
        TestProductDescriptor product = new TestProductDescriptor();
        product.descriptor = generateProductInfo();
        product.version = generateProductVersion("1.0.0");
        product.applications = Map.of("app-info.yaml", generateApplication());
        product.applicationTemplates = Map.of("app-template.yaml", generateApplicationTemplate());
        product.instanceTemplates = Map.of("instance-template.yaml", generateInstanceTemplate());
        product.launchBat = "echo \"Successfully launched on WINDOWS\"";
        product.launchSh = "echo \"Successfully launched on LINUX\"";
        return product;
    }

    private static ProductDescriptor generateProductInfo() {
        ProductDescriptor productDescriptor = new ProductDescriptor();
        productDescriptor.name = "Test Product";
        productDescriptor.product = "io.bdeploy/test";
        productDescriptor.vendor = "BDeploy Team";
        productDescriptor.applications = List.of("server-app");
        productDescriptor.applicationTemplates = List.of("app-template.yaml");
        productDescriptor.instanceTemplates = List.of("instance-template.yaml");
        productDescriptor.versionFile = "product-version.yaml";
        return productDescriptor;
    }

    public static ProductVersionDescriptor generateProductVersion(String version) {
        ProductVersionDescriptor productVersion = new ProductVersionDescriptor();
        productVersion.version = version;
        productVersion.appInfo = Map.of("server-app", Map.of(WINDOWS, "app-info.yaml", LINUX, "app-info.yaml"));
        return productVersion;
    }

    public static InstanceTemplateDescriptor generateMinimalInstanceTemplate(String templateName) {
        InstanceTemplateDescriptor tpl = new InstanceTemplateDescriptor();
        tpl.name = templateName;

        InstanceTemplateGroup group = new InstanceTemplateGroup();
        group.name = "Min Group";
        TemplateApplication app1 = new TemplateApplication();
        app1.application = "server-app";
        app1.name = "Min Application";
        app1.processControl = Map.of("startType", "MANUAL_CONFIRM");
        group.applications = List.of(app1);
        tpl.groups = new ArrayList<>();
        tpl.groups.add(group);

        return tpl;
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
        tpl.processControlGroups = List.of(pcg1, pcg2);

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
        group.applications = List.of(app1, app2);
        tpl.groups = List.of(group);
        return tpl;
    }

    private static ApplicationTemplateDescriptor generateApplicationTemplate() {
        ApplicationTemplateDescriptor tpl = new ApplicationTemplateDescriptor();
        tpl.id = "server-with-sleep";
        tpl.application = "server-app";
        tpl.name = "Server With Sleep";
        tpl.description = "A Server Application with the sleep parameter set to a given value.";
        tpl.fixedVariables = null;
        tpl.processControl = null;
        tpl.applyOn = null;

        TemplateVariable tplVar = new TemplateVariable();
        tplVar.id = "app-tpl-sleep";
        tplVar.name = "Sleep Timeout";
        tplVar.description = "The amount of time the server application should sleep";
        tplVar.type = TemplateVariableType.NUMERIC;
        tplVar.defaultValue = "750";
        tpl.templateVariables = List.of(tplVar);

        TemplateParameter param = new TemplateParameter();
        param.id = "param.sleep";
        param.value = "{{T:app-tpl-sleep}}";
        tpl.startParameters = List.of(param);

        return tpl;
    }

    private static ApplicationDescriptor generateApplication() {
        ApplicationDescriptor app = new ApplicationDescriptor();
        app.name = "Server Application";
        app.supportedOperatingSystems = List.of(WINDOWS, LINUX);

        ProcessControlDescriptor processControl = new ProcessControlDescriptor();
        processControl.gracePeriod = 3000;
        processControl.supportedStartTypes = List.of(MANUAL, MANUAL_CONFIRM, INSTANCE);
        processControl.supportsKeepAlive = true;
        processControl.noOfRetries = 2;
        app.processControl = processControl;

        ExecutableDescriptor startCommand = new ExecutableDescriptor();
        startCommand.launcherPath = "{{WINDOWS:launch.bat}}{{LINUX:launch.sh}}";
        ParameterDescriptor param = new ParameterDescriptor();
        param.id = "param.sleep";
        param.name = "Sleep Timeout";
        param.longDescription = "A numeric parameter - controls how long the application stays alive";
        param.groupName = "Sleep Configuration";
        param.parameter = "--sleep";
        param.defaultValue = new LinkedValueConfiguration("10");
        param.type = VariableType.NUMERIC;
        param.mandatory = true;
        startCommand.parameters = List.of(param);
        app.startCommand = startCommand;

        return app;
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

    public static class TestProductDescriptor {

        public ProductDescriptor descriptor;
        public ProductVersionDescriptor version;
        public Map<String, ApplicationDescriptor> applications;
        public Map<String, ApplicationTemplateDescriptor> applicationTemplates;
        public Map<String, InstanceTemplateDescriptor> instanceTemplates;
        public String launchBat;
        public String launchSh;

    }
}
