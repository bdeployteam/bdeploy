package io.bdeploy.bhive.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;

import io.bdeploy.bhive.cli.TokenTool.TokenConfig;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.common.util.PathHelper;

/**
 * Generates an access token and exports the public key for this server, given
 * the private key store.
 */
@Help("Generate, import and verify access tokens.")
@ToolCategory(BHiveCli.MAINTENANCE_TOOLS)
@CliName("token")
public class TokenTool extends ConfiguredCliTool<TokenConfig> {

    public @interface TokenConfig {

        @Help("Path to the (PKCS12) private or (JKS) public key store, depending on other parameters")
        String keystore();

        @Help("Passphrase for the keystore and any contained keys")
        String passphrase();

        @Help(value = "Create a token from the private key in the given keystore", arg = false)
        boolean create() default false;

        @Help(value = "Load a public key and token and store into the given truststore", arg = false)
        boolean load() default false;

        @Help("The signature pack of the remote to load into the truststore")
        String pack();

        @Help(value = "Validate a given token against the given private key store", arg = false)
        boolean check() default false;

        @Help(value = "Dump the current access token in the given truststore", arg = false)
        boolean dump() default false;

        @Help("The signed token value to check agains the private key store")
        String token();
    }

    public TokenTool() {
        super(TokenConfig.class);
    }

    @Override
    protected RenderableResult run(TokenConfig config) {
        helpAndFailIfMissing(config.keystore(), "Missing --keystore");
        Path ksPath = Paths.get(config.keystore());
        char[] pass = config.passphrase() == null ? null : config.passphrase().toCharArray();

        if (config.create()) {
            return createNewToken(ksPath, pass);
        } else if (config.load()) {
            helpAndFailIfMissing(config.pack(), "Missing --pack");

            importExistingToken(ksPath, pass, config.pack());
            return createSuccess();
        } else if (config.check()) {
            helpAndFailIfMissing(config.token(), "Missing --token");

            return checkExistingToken(ksPath, pass, config.token());
        } else if (config.dump()) {
            dumpExistingToken(ksPath, pass);
            return null; // special output
        } else {
            return createNoOp();
        }
    }

    private void dumpExistingToken(Path ksPath, char[] pass) {
        SecurityHelper helper = SecurityHelper.getInstance();

        try {
            KeyStore ks = helper.loadPublicKeyStore(ksPath, pass);
            out().println(helper.getSignedToken(ks, pass));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load access token from " + ksPath, e);
        }
    }

    private DataResult createNewToken(Path keystore, char[] passphrase) {
        SecurityHelper helper = SecurityHelper.getInstance();
        ApiAccessToken aat = new ApiAccessToken.Builder().forSystem().addPermission(ScopedPermission.GLOBAL_ADMIN).build();

        try {
            String pack = helper.createSignaturePack(aat, keystore, passphrase);
            out().println(pack);
        } catch (Exception e) {
            throw new IllegalStateException("cannot create signature pack", e);
        }

        return createSuccess().addField("Valid For", "50 years").addField("Issued To", aat.getIssuedTo()).addField("Permissions",
                aat.getPermissions().toString());
    }

    private static void importExistingToken(Path keystore, char[] passphrase, String sigPack) {
        SecurityHelper helper = SecurityHelper.getInstance();

        try {
            helper.importSignaturePack(sigPack, keystore, passphrase);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot import signature pack", e);
        }
    }

    private DataResult checkExistingToken(Path keystore, char[] passphrase, String token) {
        checkPrivateKeyStoreExists(keystore);

        SecurityHelper helper = SecurityHelper.getInstance();

        try {
            KeyStore ks = helper.loadPrivateKeyStore(keystore, passphrase);
            ApiAccessToken pl = helper.getVerifiedPayload(token, ApiAccessToken.class, ks);
            if (pl == null) {
                return createResultWithErrorMessage("Invalid signature.");
            } else {
                if (!pl.isValid()) {
                    return createResultWithErrorMessage("Signature valid, but token expired");
                } else {
                    return createResultWithSuccessMessage("Signature valid. Issued to " + pl.getIssuedTo() + ".");
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot verify token", e);
        }
    }

    private void checkPrivateKeyStoreExists(Path keystore) {
        if (!PathHelper.exists(keystore)) {
            out().println("You must generate a keystore manually: ");
            out().println("  openssl req -newkey rsa:2048 -nodes -keyout key.pem -x509 -days 17800 -out cert.pem");
            out().println("  openssl pkcs12 -inkey key.pem  -in cert.pem -export -out certstore.p12");

            throw new IllegalArgumentException("private keystore does not exist: " + keystore);
        }
    }

}
