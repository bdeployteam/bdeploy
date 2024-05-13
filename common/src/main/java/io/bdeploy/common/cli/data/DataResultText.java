package io.bdeploy.common.cli.data;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Map;

class DataResultText extends DataResultBase {

    public DataResultText(PrintStream output) {
        super(output);
    }

    @Override
    public void render() {
        DataTable table = DataFormat.TEXT.createTable(out());
        table.setLineWrapHint(true);

        if (getThrowable() != null) {
            table.setCaption(getMessage() != null ? getMessage() : "Error");
            table.setHideHeadersHint(true);
            table.column(new DataTableColumn("Error", 120));
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                try (PrintWriter pw = new PrintWriter(baos)) {
                    getThrowable().printStackTrace(pw);
                }
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(new ByteArrayInputStream(baos.toByteArray())))) {
                    String line = br.readLine();
                    do {
                        table.row().cell(line).build();
                    } while ((line = br.readLine()) != null);
                }
            } catch (IOException e) {
                out().println("ERROR: Cannot display error");
            }
        } else {
            table.column(new DataTableColumn("Result Field", 25));
            table.column(new DataTableColumn("Result Value", 95));

            if (getMessage() != null) {
                table.row().cell("Message").cell(getMessage()).build();
            }

            for (Map.Entry<String, String> entry : getFields().entrySet()) {
                table.row().cell(entry.getKey()).cell(entry.getValue()).build();
            }
        }
        table.render();
    }

}
