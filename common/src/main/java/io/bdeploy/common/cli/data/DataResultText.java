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

    DataResultText(PrintStream output) {
        super(output);
    }

    @Override
    public void render() {
        DataTable table = DataFormat.TEXT.createTable(out());
        table.setLineWrapHint(true);

        if (getThrowable() != null) {
            table.setCaption(getMessage() != null ? getMessage() : "Error");
            table.setHideHeadersHint(true);
            table.column(new DataTableColumn.Builder("Error").setMinWidth(100).build());
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
            table.column(new DataTableColumn.Builder("Result Field").setMinWidth(25).build());
            table.column(new DataTableColumn.Builder("Result Value").setMinWidth(50).build());

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
