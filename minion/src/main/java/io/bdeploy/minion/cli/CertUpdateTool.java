package io.bdeploy.minion.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
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
import io.bdeploy.ui.api.MinionMode;

@Help("Manage minion certificate")
@CliName("certificate")
public class CertUpdateTool extends ConfiguredCliTool<CertUpdateConfig> {

    public @interface CertUpdateConfig {

        @Help("Path to the new certificate in PEM format. This will render all existing tokens INVALID!")
        String update();

        @Help("Root directory to update.")
        @EnvironmentFallback("BDEPLOY_ROOT")
        String root();

        @Help("Write the new access token to a token file instead of printing it on the console")
        @EnvironmentFallback("BDEPLOY_TOKENFILE")
        String tokenFile();
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

        try (MinionRoot mr = new MinionRoot(root, MinionMode.TOOL, getActivityReporter())) {
            Path ks = mr.getState().keystorePath;
            char[] ksp = mr.getState().keystorePass;

            if (config.update() != null) {
                Path cert = Paths.get(config.update());
                if (!Files.isRegularFile(cert)) {
                    helpAndFail("New certificate " + cert + " does not exist!");
                }

                out().println("ATTENTION: This operation will render all existing tokens invalid. This means");
                out().println("           that all clients need to re-run the installer to update tokens.");
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
                ApiAccessToken aat = new ApiAccessToken.Builder().setIssuedTo(System.getProperty("user.name"))
                        .addCapability(ApiAccessToken.ADMIN_CAPABILITY).build();

                String pack = helper.createSignaturePack(aat, ks, ksp);

                MinionManifest mf = new MinionManifest(mr.getHive());
                MinionConfiguration cfg = mf.read();
                MinionDto minion = cfg.getMinion(mr.getState().self);
                RemoteService newRemote = new RemoteService(minion.remote.getUri(), pack);
                minion.remote = newRemote;
                mf.update(cfg);

                if (config.tokenFile() != null) {
                    Files.write(Paths.get(config.tokenFile()), pack.getBytes(StandardCharsets.UTF_8));
                } else {
                    out().println(pack);
                }
                out().println("Certificate updated.");
            } else {
                out().println("Nothing to do...");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot update certificate", e);
        }
    }

}
