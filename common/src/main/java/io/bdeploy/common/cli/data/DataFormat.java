package io.bdeploy.common.cli.data;

import java.io.IOException;
import java.io.PrintStream;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.Log;

/**
 * The different data output formats.
 */
public enum DataFormat {

    /** Output will be given in a human readable format. */
    TEXT,

    /** Output will be given in CSV. */
    CSV,

    /** Output will be given in JSON. */
    JSON;

    private static final int TERMINAL_SIZE = getTerminalSize();

    public DataTable createTable(PrintStream target) {
        switch (this) {
            case TEXT:
                return new DataTableText(target).setMaxTableLengthHint(TERMINAL_SIZE);
            case CSV:
                return new DataTableCsv(target);
            case JSON:
                return new DataTableJson(target);
        }
        throw new IllegalStateException("Unsupported DataFormat: " + this.name());
    }

    public DataResult createResult(PrintStream target) {
        switch (this) {
            case TEXT:
                return new DataResultText(target);
            case CSV:
                return new DataResultCsv(target);
            case JSON:
                return new DataResultJson(target);
        }
        throw new IllegalStateException("Unsupported DataFormat: " + this.name());
    }

    private static int getTerminalSize() {
        try (Terminal terminal = TerminalBuilder.builder().dumb(true).build()) {
            return terminal.getWidth();
        } catch (IOException e) {
            Log.warn("Failed to get terminal width.", e);
            return -1;
        }
    }
}
