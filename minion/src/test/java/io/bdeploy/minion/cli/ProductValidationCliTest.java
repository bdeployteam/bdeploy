package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.bdeploy.common.TestCliTool.StructuredOutput;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor.ApplicationType;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.instance.InstanceVariableDefinitionDescriptor;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateControlGroup;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateGroup;
import io.bdeploy.interfaces.descriptor.template.TemplateApplication;
import io.bdeploy.interfaces.descriptor.template.TemplateVariable;
import io.bdeploy.interfaces.descriptor.template.TemplateVariableType;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.cli.TestProductFactory.TestProductDescriptor;
import io.bdeploy.ui.cli.RemoteProductValidationTool;

@ExtendWith(TestMinion.class)
class ProductValidationCliTest extends BaseMinionCliTest {

    @Test
    void testValidProduct(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);

        TestProductDescriptor product = TestProductFactory.generateProduct();

        StructuredOutput output = getResult(remote, tmp, product);
        assertEquals(1, output.size());
        assertEquals("Success", output.get(0).get("message"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "a.b.c", "1-2-3", "1.x2x.3" })
    void testProductDescriptorValidation(String minMinionVersion, RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);

        TestProductDescriptor product = TestProductFactory.generateProduct();
        product.descriptor.minMinionVersion = minMinionVersion;

        StructuredOutput output = getResult(remote, tmp, product);
        assertEquals(1, output.size());

        Set<String> errors = extractBySeverity("ERROR", output);

        assertTrue(errors.contains("Minimum BDeploy version '" + minMinionVersion + "' cannot be parsed"));
    }

    @Test
    void testApplicationTemplatesValidation(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);

        TemplateVariable duplicateTemplateVariable1 = new TemplateVariable();
        TemplateVariable duplicateTemplateVariable2 = new TemplateVariable();
        TemplateVariable duplicateTemplateVariable3 = new TemplateVariable();
        duplicateTemplateVariable1.id = "duplicate-template-var-singular";
        duplicateTemplateVariable2.id = "duplicate-template-var-dual";
        duplicateTemplateVariable3.id = "duplicate-template-var-dual";
        duplicateTemplateVariable1.name = "Duplicate template variable singular";
        duplicateTemplateVariable2.name = "Duplicate template variable dual 1";
        duplicateTemplateVariable3.name = "Duplicate template variable dual 2";
        duplicateTemplateVariable1.description = "A duplicate template variable that got added twice";
        duplicateTemplateVariable2.description = "A duplicate template variable that shares its ID with another template variable";
        duplicateTemplateVariable3.description = "A duplicate template variable that shares its ID with another template variable";
        duplicateTemplateVariable1.type = TemplateVariableType.STRING;
        duplicateTemplateVariable2.type = TemplateVariableType.NUMERIC;
        duplicateTemplateVariable3.type = TemplateVariableType.BOOLEAN;

        TemplateVariable unusedTemplateVariable1 = new TemplateVariable();
        TemplateVariable unusedTemplateVariable2 = new TemplateVariable();
        TemplateVariable unusedTemplateVariable3 = new TemplateVariable();
        TemplateVariable unusedTemplateVariable4 = new TemplateVariable();
        unusedTemplateVariable1.id = "unused-tpl-var-string";
        unusedTemplateVariable2.id = "unused-tpl-var-numeric";
        unusedTemplateVariable3.id = "unused-tpl-var-boolean";
        unusedTemplateVariable4.id = "unused-tpl-var-password";
        unusedTemplateVariable1.name = "Unused STRING template variable";
        unusedTemplateVariable2.name = "Unused NUMERIC template variable";
        unusedTemplateVariable3.name = "Unused BOOLEAN template variable";
        unusedTemplateVariable4.name = "Unused PASSWORD template variable";
        unusedTemplateVariable1.description = "A template variable of type STRING and without a default value that is declared but never used";
        unusedTemplateVariable2.description = "A template variable of type NUMERIC and without a default value that is declared but never used";
        unusedTemplateVariable3.description = "A template variable of type BOOLEAN and without a default value that is declared but never used";
        unusedTemplateVariable4.description = "A template variable of type PASSWORD and without a default value that is declared but never used";
        unusedTemplateVariable1.type = TemplateVariableType.STRING;
        unusedTemplateVariable2.type = TemplateVariableType.NUMERIC;
        unusedTemplateVariable3.type = TemplateVariableType.BOOLEAN;
        unusedTemplateVariable4.type = TemplateVariableType.PASSWORD;

        ApplicationTemplateDescriptor appTplWithInvalidParameters = new ApplicationTemplateDescriptor();
        appTplWithInvalidParameters.id = "app-tpl-with-invalid-params";
        appTplWithInvalidParameters.application = "server-app";
        appTplWithInvalidParameters.name = "Invalid template with invalid process parameters";
        appTplWithInvalidParameters.description = "A named invalid application template that references parameters that do not exist and declares parameters that are not used, as well as duplicates";
        appTplWithInvalidParameters.templateVariables.add(duplicateTemplateVariable1); // add singular duplicate variable
        appTplWithInvalidParameters.templateVariables.add(duplicateTemplateVariable1); // add singular duplicate variable again
        appTplWithInvalidParameters.templateVariables.add(duplicateTemplateVariable2); // add dual duplicate variable 1
        appTplWithInvalidParameters.templateVariables.add(duplicateTemplateVariable3); // add dual duplicate variable 2
        appTplWithInvalidParameters.templateVariables.add(unusedTemplateVariable1); // add STRING parameter that is never used
        appTplWithInvalidParameters.templateVariables.add(unusedTemplateVariable2); // add NUMERIC parameter that is never used
        appTplWithInvalidParameters.templateVariables.add(unusedTemplateVariable3); // add BOOLEAN parameter that is never used
        appTplWithInvalidParameters.templateVariables.add(unusedTemplateVariable4); // add PASSWORD parameter that is never used
        appTplWithInvalidParameters.startParameters.add(TestProductFactory// add a valid fixed value parameter
                .aTemplateParameter("fixedValueParam", "fixed-value"));
        appTplWithInvalidParameters.startParameters.add(TestProductFactory// try to reference the singular duplicate template variable
                .aTemplateParameter("duplicateParam", "{{T:duplicate-template-var-singular}}"));
        appTplWithInvalidParameters.startParameters.add(TestProductFactory// add an invalid parameter reference
                .aTemplateParameter("invalidParam", "{{T:non-existant-param}}"));

        ApplicationTemplateDescriptor appTplWithInvalidProcessControl = new ApplicationTemplateDescriptor();
        appTplWithInvalidProcessControl.id = "server-with-sleep";
        appTplWithInvalidProcessControl.application = "server-app";
        appTplWithInvalidProcessControl.name = "Invalid template with invalid process control";
        appTplWithInvalidProcessControl.description = "A named invalid application template that has an invalid process control";
        appTplWithInvalidProcessControl.processControl = new HashMap<>();
        appTplWithInvalidProcessControl.processControl.put("keepAlive", true); // generate invalid keepAlive
        appTplWithInvalidProcessControl.processControl.put("autostart", true); // generate invalid autostart

        ApplicationTemplateDescriptor appTplForMissingApp1 = new ApplicationTemplateDescriptor();
        appTplForMissingApp1.id = "app-tpl-for-missing-app1";
        appTplForMissingApp1.application = "app-that-does-not-exist"; // reference non-existing application
        appTplForMissingApp1.name = "Invalid template for missing application";
        appTplForMissingApp1.description = "A named invalid application template for an application that does not exist";

        ApplicationTemplateDescriptor appTplForMissingApp2 = new ApplicationTemplateDescriptor();
        appTplForMissingApp2.id = "app-tpl-for-missing-app2";
        appTplForMissingApp2.application = "app-that-does-not-exist"; // reference non-existing application
        appTplForMissingApp2.name = null;
        appTplForMissingApp2.description = "An anonymous invalid application template for an application that does not exist";

        TestProductDescriptor product = TestProductFactory.generateProduct();
        product.applicationTemplates = new HashMap<>();
        product.applicationTemplates.put("app-template-with-invalid-parameters.yaml", appTplWithInvalidParameters);
        product.applicationTemplates.put("app-template-with-invalid-process-control.yaml", appTplWithInvalidProcessControl);
        product.applicationTemplates.put("app-template-for-missing-app1.yaml", appTplForMissingApp1);
        product.applicationTemplates.put("app-template-for-missing-app2.yaml", appTplForMissingApp2);
        product.instanceTemplates = new HashMap<>();
        product.descriptor.applicationTemplates = product.applicationTemplates.keySet().stream().toList();
        product.descriptor.instanceTemplates = new ArrayList<>();

        ApplicationDescriptor applicationDescriptor = product.applications.get("app-info.yaml");
        applicationDescriptor.processControl.supportsKeepAlive = false; // disallow keepAlive
        applicationDescriptor.processControl.supportsAutostart = false; // disallow autostart

        StructuredOutput output = getResult(remote, tmp, product);
        assertEquals(12, output.size());

        Set<String> warnings = extractBySeverity("WARNING", output);
        assertEquals(5, warnings.size());
        Set<String> errors = extractBySeverity("ERROR", output);
        assertEquals(6, errors.size());

        assertTrue(warnings.contains("Template variable 'unused-tpl-var-string'"
                + " is defined but never used in application template 'Invalid template with invalid process parameters'"));
        assertTrue(warnings.contains("Template variable 'unused-tpl-var-numeric'"
                + " is defined but never used in application template 'Invalid template with invalid process parameters'"));
        assertTrue(warnings.contains("Template variable 'unused-tpl-var-boolean'"
                + " is defined but never used in application template 'Invalid template with invalid process parameters'"));
        assertTrue(warnings.contains("Template variable 'unused-tpl-var-password'"
                + " is defined but never used in application template 'Invalid template with invalid process parameters'"));
        assertTrue(warnings.contains("Template variable 'duplicate-template-var-dual'" // The output contains this warning twice
                + " is defined but never used in application template 'Invalid template with invalid process parameters'"));

        assertTrue(errors.contains("Application template 'Invalid template with invalid process parameters'"
                + " contain(s) duplicate template variables: duplicate-template-var-singular, duplicate-template-var-dual"));

        assertTrue(errors.contains("Missing definition for used template variable 'non-existant-param' in application template"
                + " 'Invalid template with invalid process parameters'"));

        assertTrue(errors.contains("Application template 'Invalid template for missing application'"
                + " references application 'app-that-does-not-exist' which does not exist"));
        assertTrue(errors.contains("Application template '<anonymous> (app-that-does-not-exist)'"
                + " references application 'app-that-does-not-exist' which does not exist"));

        assertTrue(errors.contains("Application template 'server-with-sleep' has 'keepAlive'"
                + " enabled, but the descriptor of the application forbids it"));
        assertTrue(errors.contains("Application template 'server-with-sleep' has 'autostart'"
                + " enabled, but the descriptor of the application forbids it"));
    }

    @Test
    void testInstanceTemplateValidation(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);

        TemplateVariable duplicateTemplateVariable1 = new TemplateVariable();
        TemplateVariable duplicateTemplateVariable2 = new TemplateVariable();
        TemplateVariable duplicateTemplateVariable3 = new TemplateVariable();
        duplicateTemplateVariable1.id = "duplicate-template-var-singular";
        duplicateTemplateVariable2.id = "duplicate-template-var-dual";
        duplicateTemplateVariable3.id = "duplicate-template-var-dual";
        duplicateTemplateVariable1.name = "Duplicate template variable singular";
        duplicateTemplateVariable2.name = "Duplicate template variable dual 1";
        duplicateTemplateVariable3.name = "Duplicate template variable dual 2";
        duplicateTemplateVariable1.description = "A duplicate template variable that got added twice";
        duplicateTemplateVariable2.description = "A duplicate template variable that shares its ID with another template variable";
        duplicateTemplateVariable3.description = "A duplicate template variable that shares its ID with another template variable";
        duplicateTemplateVariable1.type = TemplateVariableType.STRING;
        duplicateTemplateVariable2.type = TemplateVariableType.NUMERIC;
        duplicateTemplateVariable3.type = TemplateVariableType.BOOLEAN;

        TemplateVariable unusedTemplateVariable1 = new TemplateVariable();
        TemplateVariable unusedTemplateVariable2 = new TemplateVariable();
        TemplateVariable unusedTemplateVariable3 = new TemplateVariable();
        TemplateVariable unusedTemplateVariable4 = new TemplateVariable();
        unusedTemplateVariable1.id = "unused-tpl-var-string";
        unusedTemplateVariable2.id = "unused-tpl-var-numeric";
        unusedTemplateVariable3.id = "unused-tpl-var-boolean";
        unusedTemplateVariable4.id = "unused-tpl-var-password";
        unusedTemplateVariable1.name = "Unused STRING template variable";
        unusedTemplateVariable2.name = "Unused NUMERIC template variable";
        unusedTemplateVariable3.name = "Unused BOOLEAN template variable";
        unusedTemplateVariable4.name = "Unused PASSWORD template variable";
        unusedTemplateVariable1.description = "A template variable of type STRING and without a default value that is declared but never used";
        unusedTemplateVariable2.description = "A template variable of type NUMERIC and without a default value that is declared but never used";
        unusedTemplateVariable3.description = "A template variable of type BOOLEAN and without a default value that is declared but never used";
        unusedTemplateVariable4.description = "A template variable of type PASSWORD and without a default value that is declared but never used";
        unusedTemplateVariable1.type = TemplateVariableType.STRING;
        unusedTemplateVariable2.type = TemplateVariableType.NUMERIC;
        unusedTemplateVariable3.type = TemplateVariableType.BOOLEAN;
        unusedTemplateVariable4.type = TemplateVariableType.PASSWORD;

        InstanceTemplateControlGroup itcg1 = new InstanceTemplateControlGroup();
        InstanceTemplateControlGroup itcg2 = new InstanceTemplateControlGroup();
        itcg1.name = "Group 1";
        itcg2.name = "Group 2";

        TemplateApplication appWithPreferedGroup1 = new TemplateApplication();
        appWithPreferedGroup1.application = "server-app";
        appWithPreferedGroup1.name = "Valid template application";
        appWithPreferedGroup1.description = "Template application that references an existing control group";
        appWithPreferedGroup1.preferredProcessControlGroup = "Group 1"; // Existing group

        TemplateApplication appWithPreferedGroup2 = new TemplateApplication();
        appWithPreferedGroup2.application = "server-app";
        appWithPreferedGroup2.name = "Invalid template application";
        appWithPreferedGroup2.description = "Template application that references a missing control group";
        appWithPreferedGroup2.preferredProcessControlGroup = "unknownGroup"; // Missing group

        TemplateApplication appWithPreferedGroup3 = new TemplateApplication();
        appWithPreferedGroup3.template = "server-with-sleep"; // References "First Group" which is another missing group

        TemplateApplication appWithStartParams = new TemplateApplication();
        appWithStartParams.application = "server-app";
        appWithStartParams.name = "Application with parameters";
        appWithStartParams.description = "A template application that defines start parameters";
        appWithStartParams.startParameters.add(TestProductFactory// add a valid fixed value parameter
                .aTemplateParameter("fixedValueParam", "fixed-value"));
        appWithStartParams.startParameters.add(TestProductFactory// try to reference the singular duplicate template variable
                .aTemplateParameter("duplicateParam", "{{T:duplicate-template-var-singular}}"));
        appWithStartParams.startParameters.add(TestProductFactory// add an invalid parameter reference
                .aTemplateParameter("invalidParam", "{{T:non-existant-param}}"));

        TemplateApplication appWithInvalidProcessControl1 = new TemplateApplication();
        appWithInvalidProcessControl1.application = "server-app";
        appWithInvalidProcessControl1.name = "Application with invalid process control";
        appWithInvalidProcessControl1.description = "A template application that attempts to apply invalid process control";
        appWithInvalidProcessControl1.processControl.put("keepAlive", true); // generate invalid keepAlive
        appWithInvalidProcessControl1.processControl.put("autostart", true); // generate invalid autostart

        ApplicationTemplateDescriptor appTplWithInvalidProcessControl = new ApplicationTemplateDescriptor();
        appTplWithInvalidProcessControl.id = "tpl-of-app-with-process-control";
        appTplWithInvalidProcessControl.application = "server-app";
        appTplWithInvalidProcessControl.name = "Invalid template with invalid process control";
        appTplWithInvalidProcessControl.description = "A named invalid application template that has an invalid process control";
        appTplWithInvalidProcessControl.processControl = new HashMap<>();
        appTplWithInvalidProcessControl.processControl.put("keepAlive", true); // generate invalid keepAlive
        TemplateApplication appWithInvalidProcessControl2 = new TemplateApplication();
        appWithInvalidProcessControl2.template = "tpl-of-app-with-process-control";

        InstanceTemplateGroup groupOfAppsWithControlGroups = new InstanceTemplateGroup();
        groupOfAppsWithControlGroups.name = "Only Group - Control Groups";
        groupOfAppsWithControlGroups.description = "All applications - Control Groups";
        groupOfAppsWithControlGroups.applications = Arrays.asList(appWithPreferedGroup1, appWithPreferedGroup2,
                appWithPreferedGroup3);

        InstanceTemplateGroup groupOfAppsWithStartParams = new InstanceTemplateGroup();
        groupOfAppsWithStartParams.name = "Only Group - Start Params";
        groupOfAppsWithStartParams.description = "All applications - Start Params";
        groupOfAppsWithStartParams.applications = Arrays.asList(appWithStartParams);

        InstanceTemplateGroup groupOfAppsWithInvalidProcessControl = new InstanceTemplateGroup();
        groupOfAppsWithInvalidProcessControl.name = "Only Group - Process Control";
        groupOfAppsWithInvalidProcessControl.description = "All applications - Process Control";
        groupOfAppsWithInvalidProcessControl.applications = Arrays.asList(appWithInvalidProcessControl1,
                appWithInvalidProcessControl2);

        InstanceTemplateDescriptor instanceTplWithMissingControlGroup = new InstanceTemplateDescriptor();
        instanceTplWithMissingControlGroup.name = "Instance template with missing control group";
        instanceTplWithMissingControlGroup.description = "An instance template with a template application that references a missing control group";
        instanceTplWithMissingControlGroup.processControlGroups = Arrays.asList(itcg1, itcg2);
        instanceTplWithMissingControlGroup.groups.add(groupOfAppsWithControlGroups);

        InstanceTemplateDescriptor instanceTplWithInvalidVariables = new InstanceTemplateDescriptor();
        instanceTplWithInvalidVariables.name = "Instance template with invalid variables";
        instanceTplWithInvalidVariables.description = "A invalid instance template that references parameters that do not exist and declares parameters that are not used, as well as duplicates";
        instanceTplWithInvalidVariables.groups.add(groupOfAppsWithStartParams);
        instanceTplWithInvalidVariables.templateVariables.add(duplicateTemplateVariable1); // add singular duplicate variable
        instanceTplWithInvalidVariables.templateVariables.add(duplicateTemplateVariable1); // add singular duplicate variable again
        instanceTplWithInvalidVariables.templateVariables.add(duplicateTemplateVariable2); // add dual duplicate variable 1
        instanceTplWithInvalidVariables.templateVariables.add(duplicateTemplateVariable3); // add dual duplicate variable 2
        instanceTplWithInvalidVariables.templateVariables.add(unusedTemplateVariable1); // add STRING parameter that is never used
        instanceTplWithInvalidVariables.templateVariables.add(unusedTemplateVariable2); // add NUMERIC parameter that is never used
        instanceTplWithInvalidVariables.templateVariables.add(unusedTemplateVariable3); // add BOOLEAN parameter that is never used
        instanceTplWithInvalidVariables.templateVariables.add(unusedTemplateVariable4); // add PASSWORD parameter that is never used

        InstanceTemplateDescriptor instanceTplWithInvalidProcessControl = new InstanceTemplateDescriptor();
        instanceTplWithInvalidProcessControl.name = "Instance template with invalid process control";
        instanceTplWithInvalidProcessControl.description = "A invalid instance template attempts to apply invalid process control";
        instanceTplWithInvalidProcessControl.groups.add(groupOfAppsWithInvalidProcessControl);

        TestProductDescriptor product = TestProductFactory.generateProduct();
        product.applicationTemplates = new HashMap<>(product.applicationTemplates);
        product.applicationTemplates.put("app-template-with-invalid-process-control.yaml", appTplWithInvalidProcessControl);
        product.instanceTemplates = new HashMap<>();
        product.instanceTemplates.put("instance-tpl-with-invalid-groups", instanceTplWithMissingControlGroup);
        product.instanceTemplates.put("instance-tpl-with-invalid-variables", instanceTplWithInvalidVariables);
        product.instanceTemplates.put("instance-tpl-with-invalid-process-control", instanceTplWithInvalidProcessControl);
        product.descriptor.applicationTemplates = product.applicationTemplates.keySet().stream().toList();
        product.descriptor.instanceTemplates = product.instanceTemplates.keySet().stream().toList();

        ApplicationDescriptor applicationDescriptor = product.applications.get("app-info.yaml");
        applicationDescriptor.processControl.supportsKeepAlive = false; // disallow keepAlive
        applicationDescriptor.processControl.supportsAutostart = false; // disallow autostart

        StructuredOutput output = getResult(remote, tmp, product);
        assertEquals(14, output.size());

        Set<String> warnings = extractBySeverity("WARNING", output);
        assertEquals(7, warnings.size());
        Set<String> errors = extractBySeverity("ERROR", output);
        assertEquals(6, errors.size());

        assertTrue(warnings.contains("Preferred process control group 'unknownGroup' for application"
                + " 'Invalid template application' is not available in the instance template 'Instance template with missing control group'"));
        assertTrue(warnings.contains("Preferred process control group 'First Group' for application"
                + " 'Server With Sleep' is not available in the instance template 'Instance template with missing control group'"));

        assertTrue(warnings.contains("Template variable 'unused-tpl-var-string'"
                + " is defined but never used in instance template 'Instance template with invalid variables'"));
        assertTrue(warnings.contains("Template variable 'unused-tpl-var-numeric'"
                + " is defined but never used in instance template 'Instance template with invalid variables'"));
        assertTrue(warnings.contains("Template variable 'unused-tpl-var-boolean'"
                + " is defined but never used in instance template 'Instance template with invalid variables'"));
        assertTrue(warnings.contains("Template variable 'unused-tpl-var-password'"
                + " is defined but never used in instance template 'Instance template with invalid variables'"));
        assertTrue(warnings.contains("Template variable 'duplicate-template-var-dual'" // The output contains this warning twice
                + " is defined but never used in instance template 'Instance template with invalid variables'"));

        assertTrue(errors.contains("Instance template 'Instance template with invalid variables'"
                + " contain(s) duplicate template variables: duplicate-template-var-singular, duplicate-template-var-dual"));

        assertTrue(errors.contains("Missing definition for used template variable 'non-existant-param' in instance template"
                + " 'Instance template with invalid variables'"));

        assertTrue(errors.contains("Application template 'tpl-of-app-with-process-control' has 'keepAlive' enabled,"
                + " but the descriptor of the application forbids it"));

        assertTrue(errors.contains("Instance template 'Instance template with invalid process control' has group"
                + " 'Only Group - Process Control' which uses application 'server-app' and sets its parameter 'keepAlive'"
                + " to enabled, but the descriptor of the application forbids it"));
        assertTrue(errors.contains("Instance template 'Instance template with invalid process control' has group"
                + " 'Only Group - Process Control' which uses application 'server-app' and sets its parameter 'autostart'"
                + " to enabled, but the descriptor of the application forbids it"));

        assertTrue(errors.contains("Instance template 'Instance template with invalid process control' has group"
                + " 'Only Group - Process Control' which uses template 'tpl-of-app-with-process-control' and sets its"
                + " parameter 'keepAlive' to enabled, but the descriptor of the application forbids it"));
    }

    @Test
    void testInstanceVariableDefinitionValidation(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);

        VariableDescriptor validVar = new VariableDescriptor();
        validVar.id = "variable-definition-valid";
        validVar.name = "Valid instance variable definition";
        validVar.longDescription = "A valid variable";

        VariableDescriptor duplicateVarSolo = new VariableDescriptor();
        duplicateVarSolo.id = "variable-definition-duplicate-solo";
        duplicateVarSolo.name = "Invalid instance variable definition - duplicate solo";
        duplicateVarSolo.longDescription = "A variable that gets added twice";

        VariableDescriptor duplicateVarDual1 = new VariableDescriptor();
        duplicateVarDual1.id = "variable-definition-duplicate-dual";
        duplicateVarDual1.name = "Invalid instance variable definition - duplicate dual 1";
        duplicateVarDual1.longDescription = "A variable that has the same id as another variable 1";

        VariableDescriptor duplicateVarDual2 = new VariableDescriptor();
        duplicateVarDual2.id = "variable-definition-duplicate-dual";
        duplicateVarDual2.name = "Invalid instance variable definition - duplicate dual 2";
        duplicateVarDual2.longDescription = "A variable that has the same id as another variable 2";

        InstanceVariableDefinitionDescriptor descr = new InstanceVariableDefinitionDescriptor();
        descr.definitions.add(validVar);
        descr.definitions.add(duplicateVarSolo); // add singular duplicate variable
        descr.definitions.add(duplicateVarSolo); // add singular duplicate variable
        descr.definitions.add(duplicateVarDual1); // add dual duplicate variable 1
        descr.definitions.add(duplicateVarDual2); // add dual duplicate variable 2

        TestProductDescriptor product = TestProductFactory.generateProduct();
        product.instanceVariableDefinitions = new HashMap<>(product.instanceVariableDefinitions);
        product.instanceVariableDefinitions.put("invalid-instance-variable-definitions.yaml", descr);
        product.descriptor.instanceVariableDefinitions = product.instanceVariableDefinitions.keySet().stream().toList();

        StructuredOutput output = getResult(remote, tmp, product);
        assertEquals(1, output.size());

        Set<String> errors = extractBySeverity("ERROR", output);
        assertEquals(1, errors.size());

        assertTrue(errors.contains("Instance variable definitions contain(s) duplicate template"
                + " variables: variable-definition-duplicate-solo, variable-definition-duplicate-dual"));
    }

    @Test
    void testAppliationValidation(RemoteService remote, @TempDir Path tmp) throws IOException {
        createInstanceGroup(remote);

        ParameterDescriptor duplicateParamSolo = new ParameterDescriptor();
        duplicateParamSolo.id = "duplicate-param-solo";
        duplicateParamSolo.name = "Duplicate parameter solo";
        duplicateParamSolo.longDescription = "A parameter that gets added twice";

        ParameterDescriptor duplicateParamDual1 = new ParameterDescriptor();
        duplicateParamDual1.id = "duplicate-param-dual";
        duplicateParamDual1.name = "Duplicate parameter dual 1";
        duplicateParamDual1.longDescription = "A parameter that has the same id as another parameter";

        ParameterDescriptor duplicateParamDual2 = new ParameterDescriptor();
        duplicateParamDual2.id = "duplicate-param-dual";
        duplicateParamDual2.name = "Duplicate parameter dual 1";
        duplicateParamDual2.longDescription = "A parameter that has the same id as another parameter";

        ParameterDescriptor parmWithDefault = new ParameterDescriptor();
        parmWithDefault.id = "param-with-default";
        parmWithDefault.name = "Parameter with default value";
        parmWithDefault.longDescription = "A normal and valid parameter with a default value";
        parmWithDefault.defaultValue = new LinkedValueConfiguration("dummy");

        ParameterDescriptor parmWithoutDefault = new ParameterDescriptor();
        parmWithoutDefault.id = "param-without-default";
        parmWithoutDefault.name = "Parameter without default value";
        parmWithoutDefault.longDescription = "A normal and valid parameter without a default value";

        TestProductDescriptor product = TestProductFactory.generateProduct();
        ApplicationDescriptor applicationDescriptor = product.applications.get("app-info.yaml");
        applicationDescriptor.type = ApplicationType.CLIENT; // Set to CLIENT to trigger the endpoint error
        applicationDescriptor.startCommand.parameters = new ArrayList<>(applicationDescriptor.startCommand.parameters);
        applicationDescriptor.startCommand.parameters.add(duplicateParamSolo); // add duplicate parameter
        applicationDescriptor.startCommand.parameters.add(duplicateParamSolo); // add duplicate parameter again
        applicationDescriptor.startCommand.parameters.add(duplicateParamDual1); // add dual duplicate parameter 1
        applicationDescriptor.startCommand.parameters.add(duplicateParamDual2); // add dual duplicate parameter 2
        applicationDescriptor.stopCommand.parameters = new ArrayList<>(applicationDescriptor.stopCommand.parameters);
        applicationDescriptor.stopCommand.parameters.add(parmWithDefault); // add a valid parameter
        applicationDescriptor.stopCommand.parameters.add(parmWithoutDefault); // add a parameter without a default value

        StructuredOutput output = getResult(remote, tmp, product);
        assertEquals(4, output.size());

        Set<String> errors = extractBySeverity("ERROR", output);
        assertEquals(4, errors.size());

        assertTrue(errors.contains("'server-app' is a client application but has endpoints configured"));

        assertTrue(errors.contains("Application 'server-app' has parameter with duplicate id 'duplicate-param-solo'"));
        assertTrue(errors.contains("Application 'server-app' has parameter with duplicate id 'duplicate-param-dual'"));

        assertTrue(errors.contains("Parameter 'param-without-default' of application 'server-app' must have a default value"));
    }

    private StructuredOutput getResult(RemoteService remote, Path tmp, TestProductDescriptor product) throws IOException {
        Path productPath = createAndUploadProduct(remote, tmp, product);
        Path validationDescriptorPath = TestProductFactory.generateAndWriteValidationDescriptor(productPath, product);
        return remote(remote, RemoteProductValidationTool.class, "--descriptor=" + validationDescriptorPath);
    }

    private static Set<String> extractBySeverity(String severity, StructuredOutput validationOutput) {
        return IntStream.range(0, validationOutput.size()).filter(i -> severity.equals(validationOutput.get(i).get("Severity")))
                .mapToObj(i -> validationOutput.get(i).get("Message")).collect(Collectors.toSet());
    }
}
