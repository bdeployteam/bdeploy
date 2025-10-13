package io.bdeploy.minion.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;

import io.bdeploy.common.audit.AuditRecord;
import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.MinionRootValidator;
import io.bdeploy.common.cfg.NonExistingOrEmptyDirPathValidator;
import io.bdeploy.common.cfg.PathOwnershipValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.minion.BCX509Helper;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.MinionState;
import io.bdeploy.minion.cli.CertUpdateTool.CertUpdateConfig;

@Help("Manage minion certificate")
@ToolCategory(MinionServerCli.MGMT_TOOLS)
@CliName("certificate")
public class CertUpdateTool extends ConfiguredCliTool<CertUpdateConfig> {

    public @interface CertUpdateConfig {

        @Help("Path to the new certificate in PEM format. The minion server will use this as HTTPS certificate instead of the internal self-signed one. "
                + "WARNING: The certificate must be valid and trusted by browser AS WELL as the Java version in use by BDeploy.")
        @Validator(ExistingPathValidator.class)
        String https();

        @Help(value = "Regenerate the internal self-signed certificate in use. WARNING: invalidates all existing tokens.",
              arg = false)
        boolean regenerate() default false;

        @Help("Regenerate and export a master access token to the given file.")
        @Validator(NonExistingOrEmptyDirPathValidator.class)
        String exportToken();

        @Help(value = "When given reverts a previous regenerate operation and restores the previous certificate and configuration.",
              arg = false)
        boolean revert() default false;

        @Help(value = "Reverts a previous HTTPS certificate update.", arg = false)
        boolean revertHttps() default false;

        @Help(value = "Removes the separate HTTPS certificate, and instead uses the self-signed certificate again.", arg = false)
        boolean removeHttps() default false;

        @Help("Root directory to update.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator({ MinionRootValidator.class, PathOwnershipValidator.class })
        String root();

        @Help(value = "Override user questions and assume consent", arg = false)
        boolean yes() default false;

        @Help("Target file to export the current certificate and key to")
        @Validator(NonExistingOrEmptyDirPathValidator.class)
        String export();

        @Help("Target file to export the current HTTPS certificate and key to.")
        @Validator(NonExistingOrEmptyDirPathValidator.class)
        String exportHttps();
    }

    public CertUpdateTool() {
        super(CertUpdateConfig.class);
    }

    @Override
    protected RenderableResult run(CertUpdateConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        Path root = Paths.get(config.root());

        if (!Files.isDirectory(root)) {
            helpAndFail("Root " + root + " does not exists!");
        }

        // validate commands; revert* and the others are mutually exclusive.
        boolean hasModCommand = config.regenerate() || config.export() != null || config.exportHttps() != null
                || config.exportToken() != null || config.https() != null;

        boolean hasRevertCommand = config.revert() || config.revertHttps() || config.removeHttps();

        if (hasModCommand && hasRevertCommand) {
            helpAndFail("Illegal combination of commands: modify and revert at the same time.");
        }

        if (!hasModCommand && !hasRevertCommand) {
            return createNoOp();
        }

        try (MinionRoot mr = new MinionRoot(root, getActivityReporter())) {
            MinionState state = mr.getState();

            if (config.https() != null) {
                checkHttps(mr, state, config.https());
            }

            if (config.regenerate()) {
                if (!config.yes()) {
                    out().println("ATTENTION: This operation will render all existing tokens invalid. This means");
                    out().println("           that all clients need to re-run the installer(s) to update tokens.");
                    out().println("           Also all existing logins (CLI, Web, ...) will have to be re-performed.");
                    out().println("           Press CTRL+C to abort, or enter to continue.");
                    System.in.read();
                }

                doRegenerateCertificate(mr, state.keystorePath, state.keystorePass);
            }

            if (config.export() != null) {
                Path pem = Paths.get(config.export());
                BCX509Helper.exportPrivateCertificateAsPem(state.keystorePath, state.keystorePass, pem);
            }

            if (config.exportHttps() != null) {
                Path pem = Paths.get(config.exportHttps());
                BCX509Helper.exportPrivateCertificateAsPem(state.keystoreHttpsPath, state.keystorePass, pem);
            }

            if (config.exportToken() != null) {
                SecurityHelper helper = SecurityHelper.getInstance();
                ApiAccessToken aat = new ApiAccessToken.Builder().forSystem().addPermission(ScopedPermission.GLOBAL_ADMIN)
                        .build();
                String pack = helper.createSignaturePack(aat, state.keystorePath, state.keystorePass);

                Files.write(Paths.get(config.exportToken()), pack.getBytes(StandardCharsets.UTF_8));
            }

            if (config.revert()) {
                doRestoreCertificate(state.keystorePath);
                updateSelf(mr, state.keystorePath, state.keystorePass);
            }

            if (config.revertHttps()) {
                doRestoreCertificate(state.keystoreHttpsPath);
            } else if (config.removeHttps()) {
                Path ks = state.keystoreHttpsPath;
                if (ks != null && PathHelper.exists(ks)) {
                    Files.copy(ks, ks.getParent().resolve(ks.getFileName().toString() + ".bak"),
                            StandardCopyOption.REPLACE_EXISTING);
                    PathHelper.deleteIfExistsRetry(ks);
                }
            }

            return createSuccess();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Cannot update certificate", e);
        }
    }

    private void checkHttps(MinionRoot mr, MinionState state, String https) throws IOException {
        Path cert = Paths.get(https);
        if (!Files.isRegularFile(cert)) {
            helpAndFail("New HTTPS certificate " + cert + " does not exist!");
        }

        boolean created = false;
        if (state.keystoreHttpsPath == null || !PathHelper.exists(state.keystoreHttpsPath)) {
            mr.initHttpKeys();
            created = true;
            state = mr.getState();
        }

        try {
            doUpdateCertificate(mr, state.keystoreHttpsPath, state.keystorePass, cert);
        } catch (RuntimeException e) {
            if (created) {
                Files.delete(state.keystoreHttpsPath);
            }
            throw e;
        }
    }

    private void doRegenerateCertificate(MinionRoot mr, Path ks, char[] ksp) {
        mr.getAuditor()
                .audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("cert-update").build());

        try {
            Files.copy(ks, ks.getParent().resolve(ks.getFileName().toString() + ".bak"), StandardCopyOption.REPLACE_EXISTING);

            // overwrites existing.
            BCX509Helper.createKeyStore(ks, ksp);

            // update our own remote
            updateSelf(mr, ks, ksp);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot re-generate certificate", e);
        }
    }

    private RenderableResult doUpdateCertificate(MinionRoot mr, Path ks, char[] ksp, Path cert) {
        mr.getAuditor()
                .audit(AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("cert-update").build());
        try {
            BCX509Helper.updatePrivateCertificate(ks, ksp, cert);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot update certificate from " + cert, e);
        }

        return createSuccess();
    }

    private static void updateSelf(MinionRoot mr, Path ks, char[] ksp) throws GeneralSecurityException, IOException {
        SecurityHelper helper = SecurityHelper.getInstance();
        ApiAccessToken aat = new ApiAccessToken.Builder().forSystem().addPermission(ScopedPermission.GLOBAL_ADMIN).build();

        String pack = helper.createSignaturePack(aat, ks, ksp);

        MinionManifest mf = new MinionManifest(mr.getHive());
        MinionConfiguration cfg = mf.read();
        MinionDto minion = cfg.getMinion(mr.getState().self);
        RemoteService newRemote = new RemoteService(minion.remote.getUri(), pack);
        minion.remote = newRemote;
        mf.update(cfg);
    }

    private RenderableResult doRestoreCertificate(Path ks) throws IOException {
        Path bak = ks.getParent().resolve(ks.getFileName().toString() + ".bak");

        if (!PathHelper.exists(bak)) {
            return createResultWithErrorMessage("No backup to restore from exists");
        }

        // need to swap keystores - orig -> tmp, bak -> orig, tmp -> bak
        Path tmp = ks.getParent().resolve(ks.getFileName().toString() + ".tmp");

        // only need to create backup if ks exists -> see "config.removeHttps"
        boolean backup = PathHelper.exists(ks);

        if (backup) {
            Files.move(ks, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(bak, ks, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, bak, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.move(bak, ks, StandardCopyOption.REPLACE_EXISTING);
        }

        return createSuccess();
    }

}
