package io.bdeploy.minion.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.bouncycastle.cms.CMSSignedData;

import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cfg.Configuration.Validator;
import io.bdeploy.common.cfg.ExistingPathValidator;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolBase.ConfiguredCliTool;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.minion.cli.PayloadTool.PayloadConfig;
import net.jsign.pe.CertificateTableEntry;
import net.jsign.pe.DataDirectoryType;
import net.jsign.pe.PEFile;

/**
 * Manages PE executable payloads.
 */
@Help("Attach a payload to a signed PE/COFF executable.")
@ToolCategory(MinionServerCli.UTIL_TOOLS)
@CliName("payload")
public class PayloadTool extends ConfiguredCliTool<PayloadConfig> {

    public @interface PayloadConfig {

        @Help("Signed PE/COFF executable to modify.")
        @Validator(ExistingPathValidator.class)
        String executable();

        @Help("The path to the payload to attach.")
        @Validator(ExistingPathValidator.class)
        String payload();

    }

    public PayloadTool() {
        super(PayloadConfig.class);
    }

    @Override
    protected RenderableResult run(PayloadConfig config) {
        helpAndFailIfMissing(config.executable(), "Missing --executable");
        helpAndFailIfMissing(config.payload(), "Missing --payload");

        Path exe = Paths.get(config.executable());
        Path payload = Paths.get(config.payload());

        // this code is basically what this does: https://blog.barthe.ph/2009/02/22/change-signed-executable/

        try {
            PEFile pe = new PEFile(exe.toFile());

            List<CMSSignedData> signatures = pe.getSignatures();
            if (signatures.isEmpty()) {
                return createResultWithMessage("No Signatures on the Executable");
            }

            // we only support a single top level signature.
            CertificateTableEntry topSignature = new CertificateTableEntry(signatures.get(0));

            // read signature and payload data
            byte[] data = Files.readAllBytes(payload);
            byte[] signature = topSignature.toBytes();

            // append data right after each other
            byte[] bytes = new byte[(signature.length + data.length + 7) & (-8)]; // bitwise round up to next multiple of 8.
            System.arraycopy(signature, 0, bytes, 0, signature.length);
            System.arraycopy(data, 0, bytes, signature.length, data.length);

            // update the executable, table size, checksum, etc.
            pe.writeDataDirectory(DataDirectoryType.CERTIFICATE_TABLE, bytes);
        } catch (IOException e) {
            return createResultWithMessage("Cannot attach payload").setException(e);
        }

        return createSuccess();
    }

}
