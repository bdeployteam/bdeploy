package io.bdeploy.ui.cli;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.CollectionHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.ApplicationValidationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor.VariableType;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.ProductUpdateService;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.cli.RemoteProcessConfigTool.ProcessManipulationConfig;
import io.bdeploy.ui.dto.ApplicationDto;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;

@Help("Manipulate configuration for processes")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-process-config")
public class RemoteProcessConfigTool extends RemoteServiceTool<ProcessManipulationConfig> {

    public @interface ProcessManipulationConfig {

        @Help("Name of the instance group")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help("ID of the instance to manipulate")
        String uuid();

        @Help("The ID of the process to manipulate")
        String process();

        @Help(value = "Show the process parameters for the given process", arg = false)
        boolean showParameters() default false;

        @Help("Set the given parameter. If there is no parameter descriptor with the given ID, a custom parameter will be added using --predecessor to determine the location")
        String set();

        @Help("The value to set the parameter to")
        String value();

        @Help("In case of adding a custom parameter this must be provided to find the correct insertion point for the custom parameter and must reference an existing and configured parameter ID")
        String predecessor();

        @Help("Remove the given parameter. Only possible if the parameter is not mandatory.")
        String remove();
    }

    public RemoteProcessConfigTool() {
        super(ProcessManipulationConfig.class);
    }

    @Override
    protected RenderableResult run(ProcessManipulationConfig config, RemoteService remote) {
        helpAndFailIfMissing(config.instanceGroup(), "--instanceGroup is missing");
        helpAndFailIfMissing(config.uuid(), "--uuid is missing");
        helpAndFailIfMissing(config.process(), "--process is missing");

        InstanceResource ir = ResourceProvider.getVersionedResource(remote, InstanceGroupResource.class, getLocalContext())
                .getInstanceResource(config.instanceGroup());

        InstanceDto instance = ir.list().stream().filter(i -> i.instanceConfiguration.id.equals(config.uuid())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Instance with ID " + config.uuid() + " does not exist."));

        InstanceNodeConfigurationListDto nodecfg = ir.getNodeConfigurations(config.uuid(), instance.instance.getTag());

        Optional<ApplicationConfiguration> appCfg = nodecfg.nodeConfigDtos.stream().map(n -> n.nodeConfiguration.applications
                .stream().filter(a -> a.id.equals(config.process())).findFirst().orElse(null)).filter(Objects::nonNull)
                .findFirst();

        if (appCfg.isEmpty()) {
            throw new IllegalArgumentException(
                    "Application with ID " + config.process() + " not found in instance with ID " + config.uuid());
        }

        ApplicationDto appDto = nodecfg.applications.stream()
                .filter(a -> a.key.getName().equals(appCfg.get().application.getName())).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot find application " + appCfg.get().application.getName() + " in currently set product"));

        if (config.showParameters()) {
            return doShowParameters(appCfg.get(), appDto);
        } else if (config.set() != null) {
            helpAndFailIfMissing(config.value(), "--value is missing");
            return doSetParameter(config, ir, instance, nodecfg, appCfg.get(), appDto);
        } else if (config.remove() != null) {
            return doRemoveParameter(config, ir, instance, nodecfg, appCfg.get(), appDto);
        }

        return createNoOp();
    }

    private RenderableResult doShowParameters(ApplicationConfiguration app, ApplicationDto dto) {
        DataTable table = createDataTable();

        table.setCaption("Start Parameters of " + app.name + " (" + app.id + ")");
        table.column("ID", 25).column("Name", 25).column("Value", 30).column("Type", 12).column("Custom", 6).column("Fixed", 5)
                .column("Mandatory", 9).column("Default", 20);

        for (var param : app.start.parameters) {
            var desc = dto.descriptor.startCommand.parameters.stream().filter(d -> d.id.equals(param.id)).findFirst()
                    .orElse(null);

            var custom = desc == null;
            var mandatory = false;
            var fixed = false;
            String name = null;
            String defVal = null;
            var type = VariableType.STRING;

            if (desc != null) {
                name = desc.name;
                defVal = desc.defaultValue != null ? desc.defaultValue.getPreRenderable() : null;
                mandatory = desc.mandatory;
                fixed = desc.fixed;
                type = desc.type;
            }

            table.row().cell(param.id).cell(name).cell(param.value != null ? param.value.getPreRenderable() : null).cell(type)
                    .cell(custom ? "*" : null).cell(fixed ? "*" : null).cell(mandatory ? "*" : null).cell(defVal).build();
        }

        return table;
    }

    private RenderableResult doSetParameter(ProcessManipulationConfig config, InstanceResource ir, InstanceDto instance,
            InstanceNodeConfigurationListDto nodecfg, ApplicationConfiguration cfg, ApplicationDto dto) {
        String paramId = config.set();
        List<ParameterDescriptor> pdescs = dto.descriptor.startCommand.parameters;
        int myDescIndex = CollectionHelper.indexOf(pdescs, p -> p.id.equals(paramId));

        boolean hasDescriptor = myDescIndex >= 0;
        if (hasDescriptor) {
            checkCanSet(pdescs.get(myDescIndex), cfg, dto.descriptor, findNodeForApp(nodecfg, cfg));
        }

        int myIndex = CollectionHelper.indexOf(cfg.start.parameters, p -> p.id.equals(paramId));
        String paramValue = config.value();

        if (myIndex != -1) {
            // Parameter already exists -> we just change its value
            var existing = cfg.start.parameters.get(myIndex);
            doSetParameterInternal(existing, pdescs.stream().filter(p -> p.id.equals(paramId)).findFirst().orElse(null),
                    paramValue, nodecfg);
        } else {
            // Parameter does not yet exist -> we create a new one
            ParameterConfiguration param = new ParameterConfiguration();
            param.id = paramId;

            if (hasDescriptor) {
                // Parameter has a descriptor -> it is not a custom parameter
                doSetParameterInternal(param, pdescs.get(myDescIndex), paramValue, nodecfg);

                boolean insertLast = myDescIndex == (pdescs.size() - 1);
                if (!insertLast) {
                    // Lookup the next descriptor which has a value in the parameter list. insert the new parameter right *before* the
                    // value for that descriptor. This handles custom parameters well as well.
                    int insertionIndex = -1;
                    for (int indexOfNextDesc = myDescIndex + 1; indexOfNextDesc < pdescs.size()
                            && insertionIndex == -1; indexOfNextDesc++) {
                        var descAtIndex = pdescs.get(indexOfNextDesc);
                        insertionIndex = CollectionHelper.indexOf(cfg.start.parameters, p -> p.id.equals(descAtIndex.id));
                    }

                    if (insertionIndex == -1) {
                        insertLast = true;
                    } else {
                        cfg.start.parameters.add(insertionIndex, param);
                    }
                }

                if (insertLast) {
                    cfg.start.parameters.add(param);
                }
            } else {
                // Parameter has no descriptor -> it is a custom parameter
                doSetParameterInternal(param, null, paramValue, nodecfg);

                // if no predecessor -> insert first
                String predecessor = config.predecessor();
                if (predecessor != null) {
                    int insertionIndex = CollectionHelper.indexOf(cfg.start.parameters, p -> p.id.equals(predecessor));
                    if (insertionIndex == -1) {
                        throw new IllegalArgumentException("Predecessor for custom parameter not found.");
                    }

                    // We want to insert *after* the element. If that element is the last one, insert at the end.
                    if (insertionIndex == (cfg.start.parameters.size() - 1)) {
                        cfg.start.parameters.add(param);
                    } else {
                        cfg.start.parameters.add(insertionIndex + 1, param);
                    }
                } else {
                    cfg.start.parameters.add(0, param);
                }
            }
        }

        checkNoMandatoryConditionalChanges(cfg, dto.descriptor, nodecfg);

        return doUpdateInstance(ir, instance, nodecfg);
    }

    private RenderableResult doRemoveParameter(ProcessManipulationConfig config, InstanceResource ir, InstanceDto instance,
            InstanceNodeConfigurationListDto nodecfg, ApplicationConfiguration cfg, ApplicationDto dto) {

        int myIndex = CollectionHelper.indexOf(cfg.start.parameters, p -> p.id.equals(config.remove()));
        ParameterDescriptor desc = dto.descriptor.startCommand.parameters.stream().filter(p -> p.id.equals(config.remove()))
                .findFirst().orElse(null);

        checkCanRemove(desc);
        doRemoveParamterInternal(cfg, dto.descriptor, findNodeForApp(nodecfg, cfg), cfg.start.parameters.remove(myIndex));
        checkNoMandatoryConditionalChanges(cfg, dto.descriptor, nodecfg);

        return doUpdateInstance(ir, instance, nodecfg);
    }

    private static void checkCanSet(ParameterDescriptor desc, ApplicationConfiguration app, ApplicationDescriptor appDesc,
            InstanceNodeConfigurationDto node) {
        if (desc.fixed) {
            throw new IllegalArgumentException("Cannot set fixed parameter value");
        }

        if (desc.condition != null) {
            var resolver = ProductUpdateService.createResolver(node, app);
            if (!ProductUpdateService.meetsCondition(appDesc, desc, resolver)) {
                throw new IllegalArgumentException("Parameter condition not met");
            }
        }
    }

    private static void doSetParameterInternal(ParameterConfiguration inputParam, ParameterDescriptor inputDesc, String value,
            InstanceNodeConfigurationListDto nodes) {
        inputParam.value = new LinkedValueConfiguration(value);
        inputParam.preRender(inputDesc);

        if (inputDesc == null || !inputDesc.global) {
            return;
        }

        // align global parameters
        for (var node : nodes.nodeConfigDtos) {                   // for each                                       node of the instance
            for (var app : node.nodeConfiguration.applications) { // for each                   application of each node of the instance
                for (var param : app.start.parameters) {          // for each parameter of each application of each node of the instance
                    String paramId = param.id;
                    if (paramId.equals(inputParam.id)) {          // for each parameter of each application of each node of the instance that has the same ID as the inputParam
                        nodes.applications.stream().flatMap(appDto -> appDto.descriptor.startCommand.parameters.stream())
                                .filter(descr -> paramId.equals(descr.id)).findAny().ifPresent(desc -> {
                                    if (!desc.global) {
                                        return;
                                    }
                                    param.value = inputParam.value;
                                    param.preRender(desc);
                                });
                    }
                }
            }
        }
    }

    private static void checkCanRemove(ParameterDescriptor desc) {
        if (desc == null) {
            return; // custom
        }

        if (desc.mandatory) {
            throw new IllegalArgumentException("Cannot remove mandatory parameter");
        }
    }

    private static void doRemoveParamterInternal(ApplicationConfiguration cfg, ApplicationDescriptor appDesc,
            InstanceNodeConfigurationDto node, ParameterConfiguration toRemove) {
        cfg.start.parameters.remove(toRemove);
        ParameterDescriptor desc = appDesc.startCommand.parameters.stream().filter(p -> p.id.equals(toRemove.id)).findAny()
                .orElse(null);

        // check whether all conditions are still met, remove things no longer supported.
        if (desc != null) {
            var resolver = ProductUpdateService.createResolver(node, cfg);
            for (var p : cfg.start.parameters) {
                if (!ProductUpdateService.meetsCondition(appDesc, desc, resolver)) {
                    // the parameter is no longer supported, need to remove it.
                    doRemoveParamterInternal(cfg, appDesc, node, p);
                }
            }
        }
    }

    private static InstanceNodeConfigurationDto findNodeForApp(InstanceNodeConfigurationListDto nodecfg,
            ApplicationConfiguration cfg) {
        return nodecfg.nodeConfigDtos.stream()
                .filter(n -> n.nodeConfiguration.applications.stream().anyMatch(a -> a.id.equals(cfg.id))).findAny()
                .orElseThrow();
    }

    //TODO We should eventually implement a handling to allow for those changes via the CLI so that the CLI becomes as powerful as the WebUI.
    private void checkNoMandatoryConditionalChanges(ApplicationConfiguration app, ApplicationDescriptor appDesc,
            InstanceNodeConfigurationListDto nodes) {
        // Check if mandatory conditional parameters must be added/removed
        var resolver = ProductUpdateService.createResolver(findNodeForApp(nodes, app), app);
        for (var pd : appDesc.startCommand.parameters) {
            if (pd.condition != null && pd.mandatory) {
                boolean paramExists = app.start.parameters.stream().anyMatch(paramConfig -> pd.id.equals(paramConfig.id));
                boolean conditionMet = ProductUpdateService.meetsCondition(appDesc, pd, resolver);
                if (!paramExists && conditionMet) {
                    throw new UnsupportedOperationException(
                            "A mandatory parameters would need to be created due to condition changes. This is only possible in the WebUI.");
                }
                if (paramExists && !conditionMet) {
                    throw new UnsupportedOperationException(
                            "A mandatory parameters would need to be deleted due to condition changes. This is only possible in the WebUI.");
                }
            }
        }
    }

    private RenderableResult doUpdateInstance(InstanceResource ir, InstanceDto instance, InstanceNodeConfigurationListDto nodes) {
        InstanceUpdateDto update = new InstanceUpdateDto(
                new InstanceConfigurationDto(instance.instanceConfiguration, nodes.nodeConfigDtos), null);

        List<ApplicationValidationDto> validation = ir.validate(instance.instanceConfiguration.id, update);

        if (validation.isEmpty()) {
            ir.update(instance.instanceConfiguration.id, update,
                    instance.managedServer != null ? instance.managedServer.hostName : null, instance.instance.getTag());
            return createSuccess();
        } else {
            DataTable table = createDataTable().setCaption("Validation Messages");
            table.column("App", 15).column("Param", 15).column("Message", 70);

            for (var msg : validation) {
                table.row().cell(msg.appId).cell(msg.paramId).cell(msg.message).build();
            }

            return table;
        }
    }
}
