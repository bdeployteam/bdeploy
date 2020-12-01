package io.bdeploy.ui.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.bouncycastle.cms.CMSSignedData;

import net.jsign.pe.CertificateTableEntry;
import net.jsign.pe.DataDirectoryType;
import net.jsign.pe.PEFile;

/**
 * Provides helpers for embedding a payload into an signed PE/COFF executable. The digital signature
 * of the executable remains valid despite that we are modifying it.
 * <p>
 * See https://blog.barthe.ph/2009/02/22/change-signed-executable/ for more details.
 * </p>
 */
public class PayloadUtils {

    /**
     * Embeds the given bytes into given signed PE/COFF executable.
     */
    public static void embed(Path executable, byte[] data) throws IOException {
        PEFile pe = new PEFile(executable.toFile());

        List<CMSSignedData> signatures = pe.getSignatures();
        if (signatures.isEmpty()) {
            throw new RuntimeException("Only signed executables can be modified.");
        }

        // we only support a single top level signature.
        CertificateTableEntry topSignature = new CertificateTableEntry(signatures.get(0));
        byte[] signature = topSignature.toBytes();

        // append data right after each other
        // bitwise round up to next multiple of 8
        byte[] bytes = new byte[(signature.length + data.length + 7) & (-8)];
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        System.arraycopy(data, 0, bytes, signature.length, data.length);

        // update the executable, table size, checksum, etc.
        pe.writeDataDirectory(DataDirectoryType.CERTIFICATE_TABLE, bytes);
    }

}
