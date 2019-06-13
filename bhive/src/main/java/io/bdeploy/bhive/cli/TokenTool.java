package io.bdeploy.bhive.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;

import io.bdeploy.bhive.cli.TokenTool.TokenConfig;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.SecurityHelper;

/**
 * Generates an access token and exports the public key for this server, given
 * the private key store.
 */
@Help("Generate, import and verify access tokens.")
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
    protected void run(TokenConfig config) {
        helpAndFailIfMissing(config.keystore(), "Missing --keystore");
        Path ksPath = Paths.get(config.keystore());
        char[] pass = config.passphrase() == null ? null : config.passphrase().toCharArray();

        if (config.create()) {
            String issuedTo = System.getProperty("user.name");

            createNewToken(ksPath, pass, issuedTo);
        } else if (config.load()) {
            helpAndFailIfMissing(config.pack(), "Missing --pack");

            importExistingToken(ksPath, pass, config.pack());
        } else if (config.check()) {
            helpAndFailIfMissing(config.token(), "Missing --token");

            checkExistingToken(ksPath, pass, config.token());
        } else if (config.dump()) {
            dumpExistingToken(ksPath, pass);
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

    private void createNewToken(Path keystore, char[] passphrase, String issuedTo) {
        SecurityHelper helper = SecurityHelper.getInstance();
        ApiAccessToken aat = new ApiAccessToken.Builder().setIssuedTo(issuedTo).addCapability(ApiAccessToken.ADMIN_CAPABILITY)
                .build();

        out().println("Generating token with 50 years validity");

        try {
            String pack = helper.createSignaturePack(aat, keystore, passphrase);

            out().println("Import the following key on the remote using the --load option of the token tool (copy & paste):");
            out().println(pack);
        } catch (Exception e) {
            throw new IllegalStateException("cannot create signature pack", e);
        }
    }

    private void importExistingToken(Path keystore, char[] passphrase, String sigPack) {
        SecurityHelper helper = SecurityHelper.getInstance();

        try {
            helper.importSignaturePack(sigPack, keystore, passphrase);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot import signature pack", e);
        }
    }

    private void checkExistingToken(Path keystore, char[] passphrase, String token) {
        checkPrivateKeyStoreExists(keystore);

        SecurityHelper helper = SecurityHelper.getInstance();

        try {
            KeyStore ks = helper.loadPrivateKeyStore(keystore, passphrase);
            ApiAccessToken pl = helper.getVerifiedPayload(token, ApiAccessToken.class, ks);
            if (pl == null) {
                out().println("Invalid signature.");
            } else {
                if (!pl.isValid()) {
                    out().println("Signature valid, but token expired");
                } else {
                    out().println("Signature valid. Issued to " + pl.getIssuedTo() + ".");
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot verify token", e);
        }
    }

    private void checkPrivateKeyStoreExists(Path keystore) {
        if (!Files.exists(keystore)) {
            out().println("You must generate a keystore manually: ");
            out().println("  openssl req -newkey rsa:2048 -nodes -keyout key.pem -x509 -days 17800 -out cert.pem");
            out().println("  openssl pkcs12 -inkey key.pem  -in cert.pem -export -out certstore.p12");

            throw new IllegalArgumentException("private keystore does not exist: " + keystore);
        }
    }

}
