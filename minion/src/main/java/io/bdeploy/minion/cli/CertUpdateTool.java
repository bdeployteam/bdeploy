package io.bdeploy.minion.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cfg.MinionRootValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.jersey.audit.AuditRecord;
import io.bdeploy.minion.BCX509Helper;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.cli.CertUpdateTool.CertUpdateConfig;

@Help("Manage minion certificate")
@CliName("certificate")
public class CertUpdateTool extends ConfiguredCliTool<CertUpdateConfig> {

    public @interface CertUpdateConfig {

        @Help("Path to the new certificate in PEM format. This will render all existing tokens INVALID!")
        @Validator(ExistingPathValidator.class)
        String update();

        @Help("Root directory to update.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        @Validator(MinionRootValidator.class)
        String root();
    }

    public CertUpdateTool() {
        super(CertUpdateConfig.class);
    }

    @Override
    protected void run(CertUpdateConfig config) {
        helpAndFailIfMissing(config.root(), "Missing --root");
        Path root = Paths.get(config.root());

        if (!Files.isDirectory(root)) {
            helpAndFail("Root " + root + " does not exists!");
        }

        try (MinionRoot mr = new MinionRoot(root, getActivityReporter())) {
            Path ks = mr.getState().keystorePath;
            char[] ksp = mr.getState().keystorePass;

            if (config.update() != null) {
                Path cert = Paths.get(config.update());
                if (!Files.isRegularFile(cert)) {
                    helpAndFail("New certificate " + cert + " does not exist!");
                }

                out().println("ATTENTION: This operation will render all existing tokens invalid. This means");
                out().println("           that all clients need to re-run the installer(s) to update tokens.");
                out().println("           Also all existing logins (CLI, Web, ...) will have to be re-performed.");
                out().println("           Press CTRL+C to abort, or enter to continue.");
                System.in.read();

                mr.getAuditor().audit(
                        AuditRecord.Builder.fromSystem().addParameters(getRawConfiguration()).setWhat("cert-update").build());
                try {
                    BCX509Helper.updatePrivateCertificate(ks, ksp, cert);
                } catch (Exception e) {
                    out().println("ERROR: Cannot update certificate!");
                    e.printStackTrace(out());
                }

                // now update the own access token.
                SecurityHelper helper = SecurityHelper.getInstance();
                ApiAccessToken aat = new ApiAccessToken.Builder().forSystem().addPermission(ApiAccessToken.ADMIN_PERMISSION)
                        .build();

                String pack = helper.createSignaturePack(aat, ks, ksp);

                MinionManifest mf = new MinionManifest(mr.getHive());
                MinionConfiguration cfg = mf.read();
                MinionDto minion = cfg.getMinion(mr.getState().self);
                RemoteService newRemote = new RemoteService(minion.remote.getUri(), pack);
                minion.remote = newRemote;
                mf.update(cfg);

                out().println("Certificate updated.");
            } else {
                out().println("Nothing to do...");
            }
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Cannot update certificate", e);
        }
    }

}
