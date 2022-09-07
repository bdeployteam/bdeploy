/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.core.di.extensions.Service;
import org.eclipse.tea.core.services.TaskingLog;
import org.eclipse.tea.library.build.config.BuildDirectories;
import org.eclipse.tea.library.build.services.TeaBuildVersionService;
import org.eclipse.tea.library.build.util.FileUtils;

import io.bdeploy.api.product.v1.DependencyFetcher;
import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.product.v1.ProductManifestBuilder;
import io.bdeploy.api.product.v1.ProductVersionDescriptor;
import io.bdeploy.api.product.v1.impl.LocalDependencyFetcher;
import io.bdeploy.api.product.v1.impl.RemoteDependencyFetcher;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.tea.plugin.server.BDeployTargetSpec;
import io.bdeploy.tea.plugin.services.BDeployApplicationBuild;
import io.bdeploy.tea.plugin.services.BDeployProductBuild;
import jakarta.ws.rs.core.UriBuilder;

public class BDeployBuildProductTask {

    private static final String PRODUCT_INFO_YAML = "product-info.yaml";
    private static final String PRODUCT_VERSIONS_YAML = "product-versions.yaml";

    private final BDeployProductBuild desc;
    private Manifest.Key key;
    private final File target;
    private final BDeployTargetSpec pushTarget;
    private final BDeployTargetSpec sourceServer;
    private final boolean cleanup;

    public BDeployBuildProductTask(BDeployProductBuild desc, File target, BDeployTargetSpec pushTarget,
            BDeployTargetSpec sourceServer, boolean cleanup) {
        this.desc = desc;
        this.target = target;
        this.pushTarget = pushTarget;
        this.sourceServer = sourceServer;
        this.cleanup = cleanup;
    }

    @Override
    public String toString() {
        return "BDeploy Product Build: " + desc.productInfo.getFileName();
    }

    @Execute
    public void build(BuildDirectories dirs, TaskingLog log, BDeployConfig cfg, TeaBuildVersionService bvs,
            @Service List<BDeployLabelProvider> labelProviders) throws Exception {
        File prodInfoDir = new File(dirs.getProductDirectory(), "prod-info");

        if (cfg.clearBHive) {
            log.info("Clearing " + target);
            FileUtils.deleteDirectory(target);
        } else {
            log.info("Using existing " + target);
        }

        String fullVersion = calculateVersion(bvs, cfg, desc.productTag);

        ActivityReporter.Stream reporter = new ActivityReporter.Stream(log.info());
        try (BHive bhive = new BHive(target.toURI(), null, reporter)) {
            // 1: generate product version file.
            ProductVersionDescriptor pvd = new ProductVersionDescriptor();
            pvd.version = fullVersion;

            for (BDeployApplicationBuild ad : desc.apps) {
                Map<OperatingSystem, String> em = pvd.appInfo.computeIfAbsent(ad.name,
                        (n) -> new EnumMap<>(OperatingSystem.class));
                em.put(ad.os, ad.source.get().getAbsolutePath());
            }

            pvd.labels.put("X-Built-by", System.getProperty("user.name"));
            pvd.labels.put("X-Built-on", InetAddress.getLocalHost().getHostName());

            // consult external providers.
            for (BDeployLabelProvider provider : labelProviders) {
                try {
                    pvd.labels.putAll(provider.getLabels(desc));
                } catch (Exception e) {
                    log.warn("Cannot get labels from " + provider.getClass() + ": " + e.toString());
                }
            }

            FileUtils.deleteDirectory(prodInfoDir);
            FileUtils.mkdirs(prodInfoDir);

            try (OutputStream os = new FileOutputStream(new File(prodInfoDir, PRODUCT_VERSIONS_YAML))) {
                os.write(StorageHelper.toRawYamlBytes(pvd));
            }

            File prodInfoYaml = new File(prodInfoDir, PRODUCT_INFO_YAML);
            try (InputStream is = Files.newInputStream(desc.productInfo)) {
                ProductDescriptor pd = StorageHelper.fromYamlStream(is, ProductDescriptor.class);
                if (pd.versionFile == null) {
                    throw new RuntimeException("versionFile=" + PRODUCT_VERSIONS_YAML + " entry is missing.");
                }
                // Copy product descriptor so that we do not lose properties that we do not know
                // (add fields are ignored during deserialization)
                FileUtils.copyFile(desc.productInfo.toFile(), prodInfoYaml);

                if (pd.configTemplates != null && !pd.configTemplates.isEmpty()) {
                    File source = desc.productInfo.getParent().resolve(pd.configTemplates).toFile();
                    if (!source.isDirectory()) {
                        throw new IllegalStateException("Cannot find " + source);
                    }
                    File cfgDir = new File(prodInfoDir, source.getName());
                    FileUtils.deleteDirectory(cfgDir);
                    FileUtils.mkdirs(cfgDir);
                    FileUtils.copyDirectory(source, cfgDir);
                }

                if (pd.pluginFolder != null && !pd.pluginFolder.isEmpty()) {
                    File source = desc.productInfo.getParent().resolve(pd.pluginFolder).toFile();
                    if (!source.isDirectory()) {
                        throw new IllegalStateException("Cannot find " + source);
                    }
                    File pluginDir = new File(prodInfoDir, source.getName());
                    FileUtils.deleteDirectory(pluginDir);
                    FileUtils.mkdirs(pluginDir);
                    FileUtils.copyDirectory(source, pluginDir);
                }

                if (pd.instanceTemplates != null && !pd.instanceTemplates.isEmpty()) {
                    copyRelatives(prodInfoDir, pd.instanceTemplates);
                }

                if (pd.applicationTemplates != null && !pd.applicationTemplates.isEmpty()) {
                    copyRelatives(prodInfoDir, pd.applicationTemplates);
                }

                if (pd.parameterTemplates != null && !pd.parameterTemplates.isEmpty()) {
                    copyRelatives(prodInfoDir, pd.parameterTemplates);
                }

                if (pd.instanceVariableTemplates != null && !pd.instanceVariableTemplates.isEmpty()) {
                    copyRelatives(prodInfoDir, pd.instanceVariableTemplates);
                }
            }

            // 2: create product and import into bhive
            RemoteService svc;
            if (this.sourceServer != null) {
                svc = new RemoteService(UriBuilder.fromUri(sourceServer.uri).build(), sourceServer.token);
            } else {
                svc = cfg.bdeployServer == null ? null
                        : new RemoteService(UriBuilder.fromUri(cfg.bdeployServer).build(), cfg.bdeployServerToken);
            }

            DependencyFetcher fetcher;
            if (svc != null) {
                fetcher = new RemoteDependencyFetcher(svc, null, reporter);
                if (pushTarget != null) {
                    DependencyFetcher origFetcher = fetcher;
                    DependencyFetcher groupFetcher = new RemoteDependencyFetcher(
                            new RemoteService(UriBuilder.fromUri(pushTarget.uri).build(), pushTarget.token),
                            pushTarget.instanceGroup, reporter);

                    // synchronizing dependency fetcher that tries the target server first, and the source server second.
                    fetcher = new DependencyFetcher() {

                        @Override
                        public synchronized SortedSet<Key> fetch(BHive hive, SortedSet<String> deps, OperatingSystem os) {
                            try {
                                return groupFetcher.fetch(hive, deps, os);
                            } catch (Exception e) {
                                return origFetcher.fetch(hive, deps, os);
                            }
                        }
                    };
                }
            } else {
                fetcher = new LocalDependencyFetcher();
            }

            log.info("Importing product from " + prodInfoYaml);
            key = ProductManifestBuilder.importFromDescriptor(prodInfoYaml.toPath(), bhive, fetcher, true);
        } finally {
            for (BDeployApplicationBuild ad : desc.apps) {
                if (cleanup && ad.cleanup != null) {
                    List<File> toClean = ad.cleanup.get();
                    for (File c : toClean) {
                        if (c.isDirectory()) {
                            FileUtils.deleteDirectory(c);
                        } else if (c.isFile()) {
                            c.delete();
                        }
                    }
                }
            }
        }

    }

    private void copyRelatives(File prodInfoDir, List<String> paths) throws IOException {
        for (String tmpl : paths) {
            Path source = desc.productInfo.getParent().resolve(tmpl);
            Path target = prodInfoDir.toPath().resolve(desc.productInfo.getParent().relativize(source));

            FileUtils.mkdirs(target.getParent().toFile());
            FileUtils.copyFile(source.toFile(), target.toFile());
        }
    }

    public Manifest.Key getKey() {
        return key;
    }

    public File getTarget() {
        return target;
    }

    static String calculateVersion(TeaBuildVersionService bvs, BDeployConfig config, String tag) {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmm");

        if (tag != null) {
            return tag.replace("%D", format.format(date));
        }

        String q = bvs.getQualifierFormat();
        if (!q.contains("%D")) {
            q += "%D";
        }

        String displayVersion = config.bdeployDisplayVersionOverride;
        if (StringHelper.isNullOrEmpty(displayVersion)) {
            displayVersion = bvs.getDisplayVersion();
        }

        return displayVersion.replace("qualifier", q.replace("%D", format.format(date)));
    }

}
