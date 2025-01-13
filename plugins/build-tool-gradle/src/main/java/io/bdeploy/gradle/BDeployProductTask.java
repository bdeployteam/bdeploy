package io.bdeploy.gradle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.ApplicationDescriptorApi;
import io.bdeploy.api.product.v1.DependencyFetcher;
import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.api.product.v1.ProductVersionDescriptor;
import io.bdeploy.api.product.v1.impl.LocalDependencyFetcher;
import io.bdeploy.api.product.v1.impl.RemoteDependencyFetcher;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.gradle.config.BDeployRepositoryServerConfig;
import io.bdeploy.gradle.extensions.ApplicationExtension;
import io.bdeploy.gradle.extensions.BDeployProductExtension;

/**
 * Builds a product into a local BHive. Requires the applications with an 'app-info.yaml' file, as well as the 'product-info.yaml'
 * along with all referenced directories and files.
 */
public class BDeployProductTask extends DefaultTask {

    private static final Logger log = LoggerFactory.getLogger(BDeployProductTask.class);

    private BDeployRepositoryServerConfig repositoryServer = new BDeployRepositoryServerConfig();
    private final DirectoryProperty localBHive;
    private boolean dryRun = false;
    private Key key;

    /**
     * @param factory the factory to create properties
     */
    @Inject
    public BDeployProductTask(ObjectFactory factory) {
        this.localBHive = factory.directoryProperty();
        this.getExtensions().create("product", BDeployProductExtension.class, factory);

        getProject().afterEvaluate(prj -> {
            if (!localBHive.isPresent()) {
                localBHive.set(prj.getLayout().getBuildDirectory().dir("productBHive"));
            }
        });

        // never up to date.
        getOutputs().upToDateWhen(e -> false);
    }

    /**
     * Executes the task
     *
     * @throws IOException in case of problems
     */
    @TaskAction
    public void perform() throws IOException {
        try {
            ActivityReporter reporter = getProject().hasProperty("verbose")
                    ? new ActivityReporter.Stream(System.out)
                    : new ActivityReporter.Null();
            DependencyFetcher fetcher = new LocalDependencyFetcher();

            // apply from extension if set, but prefer local configuration.
            BDeployProductExtension ext = getExtensions().getByType(BDeployProductExtension.class);
            File prodInfoYaml = ext.getProductInfo().getAsFile().getOrNull();
            String version = ext.getVersion().getOrElse(getProject().getVersion().toString());
            File hive = localBHive.getAsFile().get();

            if (repositoryServer.isConfigured()) {
                fetcher = new RemoteDependencyFetcher(repositoryServer.getRemote(), null, reporter);
            } else {
                log.warn("No repository server configured. Will not be able to fetch runtime dependencies.");
            }

            if (prodInfoYaml == null) {
                throw new IllegalArgumentException("product-info.yaml is not set");
            }
            if (!prodInfoYaml.exists()) {
                throw new IllegalArgumentException("product-info.yaml is does not exist: " + prodInfoYaml);
            }

            Path prodInfoLocation = prodInfoYaml.getParentFile().toPath();

            ProductVersionDescriptor pvd = new ProductVersionDescriptor();
            pvd.version = version;
            for (ApplicationExtension app : ext.getApplications()) {
                File desc = app.getYaml().getAsFile().get();
                Path relPath = prodInfoLocation.relativize(desc.toPath());

                if (!desc.isFile()) {
                    throw new IllegalArgumentException(
                            "While processing " + app.getName() + ": Cannot find application descriptor at " + desc);
                }

                if (!"app-info.yaml".equals(desc.getName())) {
                    throw new IllegalArgumentException("While processing " + app.getName()
                            + ": Application description must be named 'app-info.yaml', but is: " + desc);
                }

                Map<OperatingSystem, String> oss = new TreeMap<>();
                if (!app.getOs().isPresent() || app.getOs().get().isEmpty()) {
                    // infer from YAML
                    ApplicationDescriptorApi appDesc = readYaml(desc, ApplicationDescriptorApi.class);
                    for (OperatingSystem os : appDesc.supportedOperatingSystems) {
                        oss.put(os, relPath.toString());
                    }
                } else {
                    for (String os : app.getOs().get()) {
                        OperatingSystem target = OperatingSystem.valueOf(os);
                        oss.put(target, relPath.toString());
                    }
                }

                pvd.appInfo.put(app.getName(), oss);
            }
            for (Map.Entry<String, String> label : ext.getLabels().get().entrySet()) {
                pvd.labels.put(label.getKey(), label.getValue());
            }

            ProductDescriptor pd = readYaml(prodInfoYaml, ProductDescriptor.class);
            if (pd.versionFile == null) {
                throw new IllegalStateException(
                        "The " + prodInfoYaml + " must specify a 'versionFile' (which will be generated if it does not exist)");
            }

            File pvdFile = new File(prodInfoYaml.getParentFile(), pd.versionFile);

            if (repositoryServer.isConfigured()) {
                log.warn(" :: Repository Server: {}", repositoryServer.getRemote().getUri());
                if (Boolean.TRUE.equals(repositoryServer.isUseLogin())) {
                    var login = repositoryServer.getLogin() == null ? "<active>" : repositoryServer.getLogin();
                    var storage = repositoryServer.getLoginStorage() == null ? "<default>" : repositoryServer.getLoginStorage();
                    log.warn(" :: Repository Login: {} from {}", login, storage);
                }
            }
            log.warn(" :: Product: {}", pd.product);
            log.warn(" :: Product Version: {}", version);
            log.warn(" :: Local BHive: {}", hive);

            log.warn(" :: Applications:");
            for (var app : pvd.appInfo.entrySet()) {
                log.warn("      " + app.getKey() + " " + app.getValue().keySet());
            }

            if (dryRun) {
                log.warn(" >> DRY-RUN - Aborting");
                return;
            }

            boolean delete = false;
            try {
                if (pvdFile.exists()) {
                    log.warn("Using existing version descriptor " + pd.versionFile);
                } else {
                    pvdFile.getParentFile().mkdirs();

                    delete = true;
                    try (OutputStream os = new FileOutputStream(pvdFile)) {
                        os.write(StorageHelper.toRawYamlBytes(pvd));
                    }
                }

                try (BHive localHive = new BHive(hive.toURI(), null, reporter)) {
                    key = ProductManifestBuilder.importFromDescriptor(prodInfoYaml.toPath(), localHive, fetcher, true);
                    System.out.println(" >> Imported " + key);
                }
            } catch (Exception e) {
                log.error("Unexpected error: {}", e.toString());
                if (log.isInfoEnabled()) {
                    log.info("Exception:", e);
                }
                throw e;
            } finally {
                if (delete) {
                    pvdFile.delete();
                }
            }
        } catch (Exception e) {
            log.error("\nError while building product: {}", GradleExceptionHelper.mapExceptionCausesToReasonWithNewline(e));
            throw e;
        }
    }

    private static <T> T readYaml(File file, Class<T> type) {
        try (InputStream is = new FileInputStream(file)) {
            return StorageHelper.fromYamlStream(is, type);
        } catch (IOException ioe) {
            throw new IllegalStateException("Cannot read " + file, ioe);
        }
    }

    /**
     * @return the serve which is used to resolve runtimeDependencies of the product.
     */
    @Nested
    public BDeployRepositoryServerConfig getRepositoryServer() {
        return repositoryServer;
    }

    /**
     * @param action configuration for a repository server.
     */
    public void repositoryServer(Action<? super BDeployRepositoryServerConfig> action) {
        action.execute(repositoryServer);
    }

    /**
     * @return the directory where the local BHive is created at.
     */
    @OutputDirectory
    public DirectoryProperty getLocalBHive() {
        return localBHive;
    }

    /**
     * @return whether to actually build or just test the configuration.
     */
    @Input
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * @param dryRun whether to dryRun only.
     */
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * @return after the product has been created, retrieves the key of the product created in the local BHive.
     */
    @Internal
    public Key getKey() {
        return key;
    }

}
