package io.bdeploy.common.cli.data;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.bdeploy.common.util.StringHelper;

public class DataTableText extends DataTableBase {

    private static final char CELL_NONE = '─';
    private static final char CELL_BOTTOM = '┬';
    private static final char CELL_TOP = '┴';
    private static final char CELL_BOTH = '┼';
    private static final char CELL_SEPARATOR = '│';
    private static final char TOP_START = '┌';
    private static final char TOP_END = '┐';
    private static final char BOTTOM_END = '┘';
    private static final char BOTTOM_START = '└';
    private static final char CONTENT_END = '┤';
    private static final char CONTENT_START = '├';

    private enum HrMode {

        TOP(TOP_START, TOP_END),
        CONTENT(CONTENT_START, CONTENT_END),
        BOTTOM(BOTTOM_START, BOTTOM_END);

        char start;
        char stop;

        private HrMode(char start, char stop) {
            this.start = start;
            this.stop = stop;
        }
    }

    private int indent;
    private boolean hideHeaders;
    private boolean lineWrap;
    private boolean allowBreak;

    DataTableText(PrintStream output) {
        super(output);
    }

    @Override
    public DataTable setIndentHint(int hint) {
        this.indent = hint;
        return this;
    }

    @Override
    public DataTable setHideHeadersHint(boolean hide) {
        this.hideHeaders = hide;
        return this;
    }

    @Override
    public DataTable setLineWrapHint(boolean wrap) {
        this.lineWrap = wrap;
        return this;
    }

    @Override
    public DataTable setWordBreakHint(boolean allowBreak) {
        this.allowBreak = allowBreak;
        return this;
    }

    @Override
    public DataTable addHorizontalRuler() {
        row(Collections.singletonList(new HrCell(getColumns().size())));
        return this;
    }

    @Override
    public void render() {
        List<String> buffer = new ArrayList<>();
        List<DataTableColumn> columns = getColumns();
        List<List<DataTableCell>> rows = getRows();

        adjustColumnsWidth();

        // caption
        if (getCaption() != null) {
            buffer.add(hr(HrMode.TOP));
            buffer.add(StringHelper.repeat(" ", indent) + content(getCaption(), 0, columns.size()));
        }

        buffer.add(hr(getCaption() != null ? HrMode.CONTENT : HrMode.TOP));

        // headers
        if (!hideHeaders) {
            StringBuilder header = new StringBuilder();
            header.append(StringHelper.repeat(" ", indent));
            for (int i = 0; i < columns.size(); ++i) {
                header.append(content(columns.get(i).getLabel(), i, 1));
            }
            buffer.add(header.toString());
            buffer.add(hr(HrMode.CONTENT));
        }

        // data
        for (List<DataTableCell> raw : rows) {
            if (raw.size() == 1 && raw.get(0) instanceof HrCell) {
                buffer.add(hr(HrMode.CONTENT));
                continue;
            }

            List<List<DataTableCell>> expanded = Collections.singletonList(raw);

            if (lineWrap) {
                expanded = wrapRow(raw);
            }

            for (List<DataTableCell> data : expanded) {
                StringBuilder row = new StringBuilder();
                row.append(StringHelper.repeat(" ", indent));
                int colIndex = 0;
                for (int i = 0; i < data.size(); ++i) {
                    row.append(content(data.get(i).getData(), colIndex, data.get(i).getSpan()));
                    colIndex += data.get(i).getSpan();
                }
                buffer.add(row.toString());
            }
        }

        if (!getFooters().isEmpty()) {
            buffer.add(hr(HrMode.CONTENT));
        }
        for (String footer : getFooters()) {
            buffer.add(content(footer, 0, getColumns().size()));
        }

        buffer.add(hr(HrMode.BOTTOM));
        processBuffer(buffer);
    }

    private void adjustColumnsWidth() {
        List<DataTableColumn> columns = getColumns();
        List<List<DataTableCell>> rows = getRows();
        Map<Integer, Integer> colIdxToMaxCellLength = new HashMap<>();

        if (rows.isEmpty()) {
            return;
        }

        // calculate column width
        for (List<DataTableCell> row : rows) {
            int colIdx = 0;
            for (DataTableCell cell : row) {
                for (int i = 0; i < cell.getSpan(); i++) {
                    int cellLength = cell.getData().length() / cell.getSpan();
                    int currentMax = colIdxToMaxCellLength.getOrDefault(colIdx, -1);
                    colIdxToMaxCellLength.put(colIdx, Math.max(currentMax, cellLength));
                    colIdx++;
                }
            }
        }

        // adjust column width
        for (int i = 0; i < columns.size(); i++) {
            DataTableColumn column = columns.get(i);
            column.setMaxCellLength(colIdxToMaxCellLength.get(i));
        }
    }

    private List<List<DataTableCell>> wrapRow(List<DataTableCell> raw) {
        List<DataTableColumn> columns = getColumns();
        List<List<DataTableCell>> expandedRows = new ArrayList<>();

        Map<Integer, List<DataTableCell>> perColumn = new TreeMap<>();
        int colIndex = 0;
        for (DataTableCell item : raw) {
            List<DataTableColumn> spanning = columns.subList(colIndex, colIndex + item.getSpan());

            // width of text per column and space for separators (3 * (num columns - 1))
            int width = spanning.stream().map(DataTableColumn::getWidth).reduce(0, Integer::sum) + (3 * (spanning.size() - 1));

            String remaining = item.getData();
            while (remaining.length() > width || remaining.contains("\n")) {
                int newLine = remaining.indexOf('\n');
                int index = newLine == -1 ? width : newLine;

                if (!allowBreak && newLine == -1) {
                    while (index-- > 0) {
                        if (Character.isWhitespace(remaining.charAt(index))) {
                            break;
                        }
                    }

                    // no whitespace found.
                    if (index <= 0) {
                        index = width;
                    }
                }

                perColumn.computeIfAbsent(colIndex, k -> new ArrayList<>())
                        .add(new DataTableCell(remaining.substring(0, index), item.getSpan()));

                remaining = remaining.substring(index).trim();
            }

            perColumn.computeIfAbsent(colIndex, k -> new ArrayList<>()).add(new DataTableCell(remaining, item.getSpan()));
            colIndex += item.getSpan();
        }

        // create all rows in the result.
        Integer rowCount = perColumn.values().stream().map(List::size).reduce(0, Integer::max);
        for (int r = 0; r < rowCount; ++r) {
            expandedRows.add(new ArrayList<>());
        }

        // make all columns same length
        perColumn.forEach((idx, items) -> {
            while (items.size() < rowCount) {
                items.add(new DataTableCell("", items.get(0).getSpan()));
            }
        });

        perColumn.forEach((idx, items) -> {
            for (int i = 0; i < rowCount; ++i) {
                expandedRows.get(i).add(items.get(i));
            }
        });

        return expandedRows;
    }

    private void processBuffer(List<String> buffer) {
        for (int i = 0; i < buffer.size(); ++i) {
            String line = buffer.get(i);
            String prev = StringHelper.repeat(" ", line.length());
            String next = StringHelper.repeat(" ", line.length());

            if (i > 0) {
                prev = buffer.get(i - 1);
            }
            if (i < (buffer.size() - 1)) {
                next = buffer.get(i + 1);
            }

            StringBuilder finalLine = new StringBuilder(line);

            int index = 0;
            int from = 0;
            while ((index = line.indexOf(CELL_BOTH, from)) != -1) {
                char prevChar = prev.charAt(index);
                char nextChar = next.charAt(index);

                if (prevChar == CELL_SEPARATOR && nextChar != CELL_SEPARATOR) {
                    finalLine.setCharAt(index, CELL_TOP);
                } else if (prevChar != CELL_SEPARATOR && nextChar == CELL_SEPARATOR) {
                    finalLine.setCharAt(index, CELL_BOTTOM);
                } else if (prevChar != CELL_SEPARATOR && nextChar != CELL_SEPARATOR) {
                    finalLine.setCharAt(index, CELL_NONE);
                }

                from = index + 1;
            }

            out().println(finalLine.toString());
        }
    }

    private String content(String text, int colIndex, int span) {
        text = text.replace("\t", "    ");

        StringBuilder builder = new StringBuilder();
        List<DataTableColumn> columns = getColumns();
        List<DataTableColumn> spanning = columns.subList(colIndex, colIndex + span);

        // width of text per column and space for separators (3 * (num columns - 1))
        int width = spanning.stream().map(DataTableColumn::getWidth).reduce(0, Integer::sum) + (3 * (spanning.size() - 1));

        if (colIndex == 0) {
            // first column, print opening
            builder.append(CELL_SEPARATOR).append(' ');
        }

        builder.append(expand(text, width));

        if (colIndex + span == (columns.size())) {
            // spanning to last column, print end
            builder.append(' ').append(CELL_SEPARATOR);
        } else {
            // more columns to come, print separator
            builder.append(' ').append(CELL_SEPARATOR).append(' ');
        }
        return builder.toString();
    }

    private String hr(HrMode mode) {
        StringBuilder builder = new StringBuilder();
        List<DataTableColumn> columns = getColumns();

        builder.append(StringHelper.repeat(" ", indent)).append(mode.start).append(CELL_NONE);
        for (int i = 0; i < columns.size(); ++i) {
            DataTableColumn column = columns.get(i);

            builder.append(StringHelper.repeat(Character.toString(CELL_NONE), column.getWidth()));
            if (i != (columns.size() - 1)) {
                builder.append(CELL_NONE).append(CELL_BOTH).append(CELL_NONE);
            } else {
                builder.append(CELL_NONE).append(mode.stop);
            }
        }
        return builder.toString();
    }

    private static class HrCell extends DataTableCell {

        public HrCell(int span) {
            super("-", span);
        }
    }

}
