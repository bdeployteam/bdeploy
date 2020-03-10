/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.stream.Collectors;

import javax.ws.rs.core.UriBuilder;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.lib.Repository;
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
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.tea.plugin.server.BDeployTargetSpec;
import io.bdeploy.tea.plugin.services.BDeployApplicationBuild;

@SuppressWarnings("restriction")
public class BDeployBuildProductTask {

    private static final String PRODUCT_INFO_YAML = "product-info.yaml";
    private static final String PRODUCT_VERSIONS_YAML = "product-versions.yaml";

    private final ProductDesc desc;
    private Manifest.Key key;
    private final File target;
    private final BDeployTargetSpec pushTarget;
    private final BDeployTargetSpec sourceServer;

    public BDeployBuildProductTask(ProductDesc desc, File target, BDeployTargetSpec pushTarget, BDeployTargetSpec sourceServer) {
        this.desc = desc;
        this.target = target;
        this.pushTarget = pushTarget;
        this.sourceServer = sourceServer;
    }

    @Override
    public String toString() {
        return "BDeploy Product Build: " + desc.productInfo.getFileName();
    }

    @Execute
    public void build(BuildDirectories dirs, TaskingLog log, BDeployConfig cfg, TeaBuildVersionService bvs) throws Exception {
        File prodInfoDir = new File(dirs.getProductDirectory(), "prod-info");

        if (cfg.clearBHive) {
            log.info("Clearing " + target);
            FileUtils.deleteDirectory(target);
        } else {
            log.info("Using existing " + target);
        }

        String fullVersion = calculateVersion(bvs);

        ActivityReporter.Stream reporter = new ActivityReporter.Stream(log.info());
        try (BHive bhive = new BHive(target.toURI(), reporter)) {
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

            // try to find project for product info
            Repository repo = findRepoForProduct(desc.productInfo);
            if (repo != null) {
                pvd.labels.put("X-GIT-LocalBranch", repo.getFullBranch());
                pvd.labels.put("X-GIT-CommitId", repo.getRefDatabase().exactRef(repo.getFullBranch()).getObjectId().name());
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
                    fetcher = new DependencyFetcher() {

                        @Override
                        public SortedSet<Key> fetch(BHive hive, SortedSet<String> deps, OperatingSystem os) {
                            SortedSet<Key> k = groupFetcher.fetch(hive, deps, os);
                            if (k == null) {
                                k = origFetcher.fetch(hive, deps, os);
                            }
                            return k;
                        }
                    };
                }
            } else {
                fetcher = new LocalDependencyFetcher();
            }

            log.info("Importing product from " + prodInfoYaml);
            key = ProductManifestBuilder.importFromDescriptor(prodInfoYaml.toPath(), bhive, fetcher, true);

            // clean up old versions in the hive.
            SortedSet<Key> scan = bhive.execute(new ManifestListOperation().setManifestName(key.getName()));
            scan.removeAll(
                    scan.stream().sorted((a, b) -> b.getTag().compareTo(a.getTag())).limit(10).collect(Collectors.toList()));

            for (Key k : scan) {
                log.info("Cleaning old product version: " + k);
                bhive.execute(new ManifestDeleteOperation().setToDelete(k));
            }

            if (!scan.isEmpty()) {
                bhive.execute(new PruneOperation());
            }
        }

    }

    public Manifest.Key getKey() {
        return key;
    }

    public File getTarget() {
        return target;
    }

    private Repository findRepoForProduct(Path productInfo) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IContainer[] containers = root.findContainersForLocationURI(productInfo.toAbsolutePath().toUri());

        if (containers == null || containers.length == 0) {
            return null;
        }

        IProject prj = containers[0].getProject();
        if (prj == null) {
            return null;
        }

        RepositoryMapping mapping = RepositoryMapping.getMapping(prj);
        if (mapping == null) {
            return null;
        }

        return mapping.getRepository();
    }

    private String calculateVersion(TeaBuildVersionService bvs) {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmm");

        if (desc.productTag != null) {
            return desc.productTag.replace("%D", format.format(date));
        }

        String q = bvs.getQualifierFormat();
        if (!q.contains("%D")) {
            q += "%D";
        }

        return bvs.getDisplayVersion().replace("qualifier", q.replace("%D", format.format(date)));
    }

    static class ProductDesc {

        Path productInfo;
        String productTag;
        List<BDeployApplicationBuild> apps = new ArrayList<>();
    }

}
