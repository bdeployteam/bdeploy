package io.bdeploy.ui.branding;

import java.io.File;
import java.io.IOException;

import org.boris.pecoff4j.PE;
import org.boris.pecoff4j.SectionData;
import org.boris.pecoff4j.SectionTable;
import org.boris.pecoff4j.io.PEAssembler;
import org.boris.pecoff4j.io.PEParser;

import com.google.common.primitives.Bytes;

import io.bdeploy.bhive.util.StorageHelper;

/**
 * Responsible for branding the native Windows executable so that it uses the icon defined by the application descriptor.
 */
public class Branding {

    private static final String START_BDEPLOY = "###START_BDEPLOY###";
    private static final String START_CONFIG = "###START_CONFIG###";
    private static final String END_CONFIG = "###END_CONFIG###";
    private static final String END_BDEPLOY = "###END_BDEPLOY###";

    private final PE app;

    /**
     * Creates a new instance that will brand the given executable
     *
     * @param executable the executable
     */
    public Branding(File executable) throws IOException {
        this.app = PEParser.parse(executable);
    }

    /**
     * Updates the embedded configuration file.
     */
    public void updateConfig(BrandingConfig config) throws Exception {
        SectionTable sectionTable = app.getSectionTable();
        SectionData codeContent = sectionTable.findSection(".text");

        // Find block that we are going to exchange
        byte[] codeBlock = codeContent.getData();
        int startBlock = Bytes.indexOf(codeBlock, START_BDEPLOY.getBytes("UTF-8"));
        int endBlock = Bytes.indexOf(codeBlock, END_BDEPLOY.getBytes("UTF-8")) + END_BDEPLOY.length();
        int length = endBlock - startBlock;

        // Build a block of the same size. Fill with dummy at the end
        StringBuilder replacement = new StringBuilder();
        replacement.append(START_BDEPLOY);
        replacement.append(START_CONFIG);
        replacement.append(new String(StorageHelper.toRawBytes(config)));
        replacement.append(END_CONFIG);

        int currentSize = replacement.toString().getBytes("UTF-8").length;
        int charsToWrite = length - currentSize - END_BDEPLOY.length();
        for (int i = 0; i < charsToWrite; i++) {
            replacement.append("0");
        }
        replacement.append(END_BDEPLOY);

        // Replace previous block with the new one
        byte[] configBlock = replacement.toString().getBytes("UTF-8");
        byte[] newData = new byte[codeBlock.length];
        System.arraycopy(codeBlock, 0, newData, 0, startBlock);
        System.arraycopy(configBlock, 0, newData, startBlock, configBlock.length);
        System.arraycopy(codeBlock, endBlock, newData, endBlock, codeBlock.length - endBlock);
        codeContent.setData(newData);
    }

    /**
     * Writes the modified executable to the given file
     */
    public void write(File targetFile) throws IOException {
        PEAssembler.write(app, targetFile);
    }

}
