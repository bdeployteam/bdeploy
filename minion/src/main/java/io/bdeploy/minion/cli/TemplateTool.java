package io.bdeploy.minion.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.core.UriBuilder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ExecutableDescriptor;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.minion.cli.TemplateTool.TemplateConfig;
import io.bdeploy.ui.api.Minion;

@Help("Creates templates and re-imports configurations from them")
@CliName("template")
public class TemplateTool extends ConfiguredCliTool<TemplateConfig> {

    public @interface TemplateConfig {

        @Help("Path to the local hive used for loading application descriptors and storing the dfeployment manifests when loading")
        String hive();

        @Help("Target path to put template in, or source path to import config from")
        String template();

        @Help("The product manifest to include templates for")
        String product();

        @Help(value = "Create a deployment template", arg = false)
        boolean create() default false;

        @Help(value = "Load configuration from template path and store in the given hive", arg = false)
        boolean load() default false;

        @Help(value = "Create an application manifest template.", arg = false)
        boolean app() default false;
    }

    public TemplateTool() {
        super(TemplateConfig.class);
    }

    @Override
    protected void run(TemplateConfig config) {
        if (config.app()) {
            // does not require anything :)
            createAppTemplate();
            return;
        }

        helpAndFailIfMissing(config.template(), "Missing --template");
        helpAndFailIfMissing(config.hive(), "Missing --hive");

        Path templateDir = Paths.get(config.template());

        try (BHive hive = new BHive(Paths.get(config.hive()).toUri(), getActivityReporter())) {
            if (config.create()) {
                helpAndFailIfMissing(config.product(), "Missing --product");
                if (Files.isDirectory(templateDir)) {
                    helpAndFail("Template directory already exists, refusing to overwrite");
                }

                try {
                    createTemplates(templateDir, hive, config.product());
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot create templates", e);
                }
            } else if (config.load()) {
                if (!Files.isDirectory(templateDir)) {
                    helpAndFail("Template directory is missing, cannot load");
                }

                loadConfiguration(templateDir, hive);
            } else {
                helpAndFail("Missing --create or --load");
            }
        }
    }

    private void createAppTemplate() {
        ApplicationDescriptor ad = new ApplicationDescriptor();
        ad.name = "DemoApplication";
        ad.configFiles.put("myconfig.json", "myconfig.json.template");
        ad.runtimeDependencies.add("jdk:1.8.0");

        ad.startCommand = new ExecutableDescriptor();
        ad.startCommand.launcherPath = "launcher.sh";

        ParameterDescriptor p1 = new ParameterDescriptor();
        p1.uid = "parameter.1";
        p1.groupName = "Group1";
        p1.longDescription = "Long parameter description";
        p1.parameter = "--parameter";
        p1.defaultValue = "default";
        p1.hasValue = true;
        p1.name = "Some Parameter";
        ad.startCommand.parameters.add(p1);

        ParameterDescriptor p2 = new ParameterDescriptor();
        p2.uid = "parameter.2";
        p2.parameter = "--other";
        p2.defaultValue = "default";
        p2.hasValue = true;
        p2.name = "Some Other Parameter";
        ad.startCommand.parameters.add(p2);

        ad.stopCommand = new ExecutableDescriptor();
        ad.stopCommand.launcherPath = "stop.sh";

        out().print(new String(StorageHelper.toRawYamlBytes(ad), StandardCharsets.UTF_8));
    }

    /**
     * This is basically what any configuration UI will have to do.
     */
    private void loadConfiguration(Path templateDir, BHive hive) {
        // read Template
        Path configPath = templateDir.resolve("config");
        Path template = templateDir.resolve("template.json");

        Template tpl;
        try (InputStream is = Files.newInputStream(template)) {
            tpl = StorageHelper.fromStream(is, Template.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read template", e);
        }

        // assign a new tag on every update.
        InstanceManifest.Builder imfb = new InstanceManifest.Builder().setInstanceConfiguration(tpl.config);

        // read all MinionTemplate
        for (Map.Entry<String, MinionTemplate> entry : tpl.minions.entrySet()) {
            InstanceNodeManifest.Builder inmBuilder = new InstanceNodeManifest.Builder();

            // this is not how this should work in reality - each node should have
            // it's own config directory with only files required by applications on this
            // node..
            inmBuilder.setConfigSource(configPath);
            inmBuilder.setInstanceNodeConfiguration(entry.getValue().config);
            inmBuilder.setMinionName(entry.getKey());

            Manifest.Key inmKey = inmBuilder.insert(hive);
            imfb.addInstanceNodeManifest(entry.getKey(), inmKey);
        }

        imfb.insert(hive);
    }

    private void createTemplates(Path templateDir, BHive hive, String product) throws IOException {
        // /actual/ configuration UI must create a 'config' dir per node with only
        // the files specifically for that node. This is skipped here for simplicity.
        Path configPath = templateDir.resolve("config");
        Path template = templateDir.resolve("template.json");

        PathHelper.mkdirs(templateDir);

        // find application manifests, copy template config
        SortedMap<Manifest.Key, ApplicationDescriptor> applications = loadApplicationsAndConfig(hive, product, configPath);

        Template tpl = new Template();
        tpl.config.uuid = UuidHelper.randomId();
        tpl.config.name = "My Deployment ";
        tpl.config.product = Manifest.Key.parse(product);
        tpl.config.target = new RemoteService(UriBuilder.fromUri("https://localhost:7701/api").build(), "DUMMY");
        tpl.config.purpose = InstancePurpose.DEVELOPMENT;

        MinionTemplate masterTpl = new MinionTemplate();
        tpl.minions.put(Minion.DEFAULT_MASTER_NAME, masterTpl);

        // read parameters from application manifest and generate template launch config
        InstanceNodeConfiguration dc = new InstanceNodeConfiguration();
        dc.name = tpl.config.name;
        dc.uuid = tpl.config.uuid;
        masterTpl.config = dc;

        applications.entrySet().stream().map(this::createAppConfigTemplate).forEach(dc.applications::add);

        // write template.
        Files.write(template, StorageHelper.toRawBytes(tpl));

        out().println("Wrote template to " + templateDir);
    }

    private ApplicationConfiguration createAppConfigTemplate(Map.Entry<Manifest.Key, ApplicationDescriptor> app) {
        ApplicationDescriptor ad = app.getValue();
        ApplicationConfiguration cfg = new ApplicationConfiguration();
        cfg.application = app.getKey();
        cfg.name = "My " + ad.name;
        cfg.processControl = new ProcessControlConfiguration();
        cfg.processControl.gracePeriod = TimeUnit.SECONDS.toMillis(30);
        cfg.processControl.startType = ApplicationStartType.INSTANCE;
        cfg.processControl.keepAlive = true;
        cfg.processControl.noOfRetries = 5;
        cfg.start = createCmdConfigTemplate(ad.startCommand);
        cfg.stop = createCmdConfigTemplate(ad.stopCommand);
        return cfg;
    }

    private CommandConfiguration createCmdConfigTemplate(ExecutableDescriptor ed) {
        if (ed == null) {
            return null;
        }

        CommandConfiguration r = new CommandConfiguration();
        r.executable = ed.launcherPath;
        for (ParameterDescriptor pd : ed.parameters) {
            if (!pd.mandatory) {
                continue;
            }
            String value = pd.defaultValue;
            if (value == null && pd.hasValue) {
                value = "DUMMY";
            }
            ParameterConfiguration pc = new ParameterConfiguration();
            pc.uid = pd.uid;
            pc.value = value;
            pc.preRendered.addAll(pd.preRender(value));

            r.parameters.add(pc);
        }

        return r;
    }

    /**
     * @param hive the {@link BHive} to read from
     * @param apps the application manifest keys to load.
     * @param configPath the path where to put configuration template files.
     * @return the list of {@link ApplicationDescriptor}s for further template
     *         processing.
     */
    private SortedMap<Manifest.Key, ApplicationDescriptor> loadApplicationsAndConfig(BHive hive, String product,
            Path configPath) {
        SortedMap<Manifest.Key, ApplicationDescriptor> result = new TreeMap<>();
        ProductManifest pmf = ProductManifest.of(hive, Manifest.Key.parse(product));

        TreeSet<ApplicationManifest> appMfs = pmf.getApplications().stream().map(k -> ApplicationManifest.of(hive, k))
                .collect(Collectors.toCollection(TreeSet::new));

        for (ApplicationManifest m : appMfs) {
            result.put(m.getKey(), m.getDescriptor());
            m.exportConfigTemplatesTo(hive, configPath);
        }

        return result;
    }

    private static final class Template {

        public InstanceConfiguration config = new InstanceConfiguration();

        public final SortedMap<String, MinionTemplate> minions = new TreeMap<>();

    }

    private static final class MinionTemplate {

        public InstanceNodeConfiguration config;

    }
}
