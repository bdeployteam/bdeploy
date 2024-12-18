package io.bdeploy.common.cli.data;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.bdeploy.common.util.StringHelper;

class DataTableText extends DataTableBase {

    /** A border of {@value #SAFETY_BORDER_WIDTH} is required to stop the terminal from misbehaving. */
    private static final int SAFETY_BORDER_WIDTH = 1;
    private static final int MIN_MAX_TABLE_LENGTH = 80;

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

    private static final String ELLIPSIS = "...";
    private static final int ELLIPSIS_LENGTH = ELLIPSIS.length();

    private static final String TABLE_CELL_PADDING = " ";
    private static final int TABLE_CELL_PADDING_WIDTH = TABLE_CELL_PADDING.length();
    private static final int DOUBLE_TABLE_CELL_PADDING_WIDTH = 2 * TABLE_CELL_PADDING_WIDTH;

    private boolean hideHeaders = false;
    private boolean lineWrap = false;
    private boolean allowBreak = false;
    private int indent = 0;
    private int maxTableLength = 0;

    DataTableText(PrintStream output) {
        super(output);
    }

    @Override
    public DataTable addHorizontalRuler() {
        row(Collections.singletonList(new HorizontalRulerCell()));
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
    public DataTable setIndentHint(int indent) {
        this.indent = indent;
        return this;
    }

    @Override
    public DataTable setMaxTableLengthHint(int maxTableLength) {
        if (maxTableLength > 0 && maxTableLength < MIN_MAX_TABLE_LENGTH) {
            maxTableLength = MIN_MAX_TABLE_LENGTH;
        }
        this.maxTableLength = maxTableLength;
        return this;
    }

    @Override
    public void doRender() {
        // Calculate column widths
        int[] columnWidths = calculateColumnWidths();

        // We have the widths, now let's construct the lines!
        List<String> lines = new ArrayList<>();

        // Start of table
        lines.add(createHorizontalLine(columnWidths, HrMode.TOP));

        // Caption
        if (caption != null) {
            lines.add(content(columnWidths, caption, 0, columns.size()));
            lines.add(createHorizontalLine(columnWidths, HrMode.CONTENT));
        }

        // Table column headers
        if (!hideHeaders) {
            StringBuilder header = new StringBuilder();
            for (int i = 0; i < columns.size(); ++i) {
                header.append(content(columnWidths, columns.get(i).getLabel(), i, 1));
            }
            lines.add(header.toString());
            lines.add(createHorizontalLine(columnWidths, HrMode.CONTENT));
        }

        // Data
        for (List<DataTableCell> row : rows) {
            if (row.size() == 1 && row.get(0) instanceof HorizontalRulerCell) {
                lines.add(createHorizontalLine(columnWidths, HrMode.CONTENT));
                continue;
            }

            List<List<DataTableCell>> wrapped = Collections.singletonList(row);
            if (lineWrap) {
                wrapped = wrapRow(columnWidths, row);
            }

            for (List<DataTableCell> wrappedRow : wrapped) {
                StringBuilder sb = new StringBuilder();
                int colIndex = 0;
                for (DataTableCell cell : wrappedRow) {
                    sb.append(content(columnWidths, cell.getData(), colIndex, cell.getSpan()));
                    colIndex += cell.getSpan();
                }
                lines.add(sb.toString());
            }
        }

        // Footers
        if (!footers.isEmpty()) {
            lines.add(createHorizontalLine(columnWidths, HrMode.CONTENT));
        }
        for (String footer : footers) {
            lines.add(content(columnWidths, footer, 0, columns.size()));
        }

        // End of table
        lines.add(createHorizontalLine(columnWidths, HrMode.BOTTOM));

        // Add indent
        String indentString = StringHelper.repeat(" ", indent);
        lines = lines.stream().map(line -> indentString + line).collect(Collectors.toList());

        // Print to output
        processLines(lines);
    }

    /**
     * Calculates the widths of the columns so that all text is fully displayed.
     */
    private int[] calculateColumnWidths() {
        // Calculate base column widths
        List<Column> cols = columns.stream()
                .map(c -> new Column(c.getMinimumWidth(), c.getLabel().length(), c.getScaleToContent()))
                .collect(Collectors.toList());
        if (hideHeaders) {
            cols.forEach(col -> col.width = col.minWidth);
        } else {
            cols.forEach(col -> col.width = Math.max(col.minWidth, col.labelLength));
        }
        for (List<DataTableCell> row : rows) {
            int colIdx = 0;
            for (DataTableCell cell : row) {
                int span = cell.getSpan();
                int alreadyAvailableSpace = 0;
                for (int i = 1; i < cell.getSpan(); i++) {
                    alreadyAvailableSpace += 1 + DOUBLE_TABLE_CELL_PADDING_WIDTH + cols.get(colIdx + i).width;
                }
                Column col = cols.get(colIdx);
                col.width = Math.max(col.width, cell.getData().length() - alreadyAvailableSpace);
                colIdx += span;
            }
        }

        // Calculated longest caption/footer
        Set<Integer> peripheralWidths = new HashSet<>();
        if (caption != null) {
            peripheralWidths.add(caption.length());
        }
        for (String footer : footers) {
            peripheralWidths.add(footer.length());
        }
        int maxPeriperalWidth = peripheralWidths.stream().mapToInt(Integer::intValue).max().orElseGet(() -> 0);

        // Enlarge last column to fit the peripheral width
        int columnCount = columns.size();
        int totalColumnsWidth = getTotalColumnsWidth(cols);
        int necessaryEnlargement = maxPeriperalWidth - totalColumnsWidth;
        if (necessaryEnlargement > 0) {
            cols.get(columnCount - 1).width += necessaryEnlargement;
        }

        // If we are done -> return
        if (maxTableLength <= 0) {
            return mapColsToWidth(cols);
        }
        int totalTableWidth = getTotalColumnsWidth(cols) + DOUBLE_TABLE_CELL_PADDING_WIDTH + 2;
        int requiredShrinkage = SAFETY_BORDER_WIDTH + indent + totalTableWidth - maxTableLength;
        if (requiredShrinkage <= 0) {
            return mapColsToWidth(cols);
        }

        // If possible, undo as much of the enlargement as necessary and return
        if (requiredShrinkage <= necessaryEnlargement) {
            cols.get(columnCount - 1).width -= requiredShrinkage;
            return mapColsToWidth(cols);
        }

        // Undo the enlargement and calculate the remaining required shrinkage
        if (necessaryEnlargement > 0) {
            cols.get(columnCount - 1).width -= necessaryEnlargement;
            requiredShrinkage -= necessaryEnlargement;
        }

        // First we shrink them down to the higher one of their caps
        List<Column> minWidthCols = cols.stream().filter(col -> !col.resizeToContent).collect(Collectors.toList());
        Collections.reverse(minWidthCols);
        for (Column col : minWidthCols) {
            int cap = Math.max(col.labelLength, col.minWidth);
            if (cap == 0) {
                cap = 1;
            }
            int removeableTillCap = col.width - cap;
            if (requiredShrinkage <= removeableTillCap) {
                col.width -= requiredShrinkage;
                return mapColsToWidth(cols);
            }
            col.width -= removeableTillCap;
            requiredShrinkage -= removeableTillCap;
        }

        // Then we shrink them down to the length of the ellipsis +1
        int ellipsiscap = 1 + ELLIPSIS_LENGTH;
        for (Column col : minWidthCols) {
            if (col.minWidth >= ellipsiscap) {
                continue;
            }
            int removeableTillCap = col.width - ellipsiscap;
            if (requiredShrinkage <= removeableTillCap) {
                col.width -= requiredShrinkage;
                return mapColsToWidth(cols);
            }
            col.width -= removeableTillCap;
            requiredShrinkage -= removeableTillCap;
        }

        // Next we shrink them down to a single character
        for (Column col : minWidthCols) {
            if (col.minWidth >= 1) {
                continue;
            }
            int removeableTillCap = col.width - 1;
            if (requiredShrinkage <= removeableTillCap) {
                col.width -= requiredShrinkage;
                return mapColsToWidth(cols);
            }
            col.width -= removeableTillCap;
            requiredShrinkage -= removeableTillCap;
        }

        // Then we shrink them down to their minimum
        for (Column col : minWidthCols) {
            int cap = col.minWidth;
            int removeableTillCap = col.width - cap;
            if (requiredShrinkage <= removeableTillCap) {
                col.width -= requiredShrinkage;
                return mapColsToWidth(cols);
            }
            col.width -= removeableTillCap;
            requiredShrinkage -= removeableTillCap;
        }

        // If we are still above the maximum allowed width, we allow it. We did what we could.
        return mapColsToWidth(cols);
    }

    private static String createHorizontalLine(int[] columnWidths, HrMode mode) {
        String paddingFiller = StringHelper.repeat(Character.toString(CELL_NONE), TABLE_CELL_PADDING_WIDTH);
        StringBuilder builder = new StringBuilder();
        builder.append(mode.start);
        builder.append(paddingFiller);
        for (int i = 0; i < columnWidths.length; i++) {
            builder.append(StringHelper.repeat(Character.toString(CELL_NONE), columnWidths[i]));
            builder.append(paddingFiller);
            if (i < columnWidths.length - 1) {
                builder.append(CELL_BOTH).append(paddingFiller);
            } else {
                builder.append(mode.stop);
            }
        }
        return builder.toString();
    }

    private String content(int[] columnWidths, String text, int startColIndex, int span) {
        text = text.replace("\t", "    ");

        int endColIndex = startColIndex + span;
        int width = getSumOfColumnWidths(columnWidths, startColIndex, endColIndex);

        String lengthAdjustedText;
        if (text.length() <= width) {
            lengthAdjustedText = text + StringHelper.repeat(TABLE_CELL_PADDING, width - text.length());
        } else if (width <= ELLIPSIS_LENGTH) {
            lengthAdjustedText = text.substring(0, width);
        } else {
            lengthAdjustedText = text.substring(0, width - ELLIPSIS_LENGTH) + ELLIPSIS;
        }

        StringBuilder builder = new StringBuilder();
        if (startColIndex == 0) {
            builder.append(CELL_SEPARATOR).append(TABLE_CELL_PADDING);
        }
        builder.append(lengthAdjustedText);
        builder.append(TABLE_CELL_PADDING).append(CELL_SEPARATOR);
        if (endColIndex < columns.size()) {
            builder.append(TABLE_CELL_PADDING);
        }
        return builder.toString();
    }

    private List<List<DataTableCell>> wrapRow(int[] columnWidths, List<DataTableCell> row) {
        List<List<DataTableCell>> wrappedRows = new ArrayList<>();

        Map<Integer, List<DataTableCell>> perColumn = new TreeMap<>();
        int colIndex = 0;
        for (DataTableCell cell : row) {
            int width = getSumOfColumnWidths(columnWidths, colIndex, colIndex + cell.getSpan());

            String remaining = cell.getData();
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
                        .add(new DataTableCell(remaining.substring(0, index), cell.getSpan()));

                remaining = remaining.substring(index).trim();
            }

            perColumn.computeIfAbsent(colIndex, k -> new ArrayList<>()).add(new DataTableCell(remaining, cell.getSpan()));
            colIndex += cell.getSpan();
        }

        // create all rows in the result.
        int rowCount = perColumn.values().stream().map(List::size).reduce(0, Integer::max);
        for (int r = 0; r < rowCount; ++r) {
            wrappedRows.add(new ArrayList<>());
        }

        // make all columns same length
        perColumn.forEach((idx, items) -> {
            while (items.size() < rowCount) {
                items.add(new DataTableCell("", items.get(0).getSpan()));
            }
        });

        perColumn.forEach((idx, items) -> {
            for (int i = 0; i < rowCount; ++i) {
                wrappedRows.get(i).add(items.get(i));
            }
        });

        return wrappedRows;
    }

    /**
     * Calculates and returns the total width of all columns including paddings and table column separator chars.
     */
    private static int getTotalColumnsWidth(List<Column> columns) {
        return columns.stream().mapToInt(col -> col.width).sum() + (1 + DOUBLE_TABLE_CELL_PADDING_WIDTH) * (columns.size() - 1);
    }

    /**
     * Calculates and returns the total width of all columns from startIndex to endIndex, including paddings and table column
     * separator chars.
     */
    private static int getSumOfColumnWidths(int[] columnWidths, int startIndex, int endIndex) {
        int[] subset = Arrays.copyOfRange(columnWidths, startIndex, endIndex);
        return IntStream.of(subset).sum() + (1 + DOUBLE_TABLE_CELL_PADDING_WIDTH) * (subset.length - 1);
    }

    private static int[] mapColsToWidth(List<Column> cols) {
        return cols.stream().mapToInt(col -> col.width).toArray();
    }

    private void processLines(List<String> lines) {
        int lastIndex = lines.size() - 1;
        for (int i = 0; i < lines.size(); ++i) {
            String line = lines.get(i);
            String prev = StringHelper.repeat(" ", line.length());
            String next = StringHelper.repeat(" ", line.length());

            if (i > 0) {
                prev = lines.get(i - 1);
            }
            if (i < lastIndex) {
                next = lines.get(i + 1);
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

            output.println(finalLine.toString());
        }
    }

    private enum HrMode {

        TOP(TOP_START, TOP_END),
        CONTENT(CONTENT_START, CONTENT_END),
        BOTTOM(BOTTOM_START, BOTTOM_END);

        private final char start;
        private final char stop;

        private HrMode(char start, char stop) {
            this.start = start;
            this.stop = stop;
        }
    }

    private static class HorizontalRulerCell extends DataTableCell {

        private HorizontalRulerCell() {
            super(null, -1);
        }
    }

    private static class Column {

        private final int minWidth;
        private final int labelLength;
        private final boolean resizeToContent;
        private int width;

        private Column(int minWidth, int labelLength, boolean resizeToContent) {
            this.minWidth = minWidth;
            this.labelLength = labelLength;
            this.resizeToContent = resizeToContent;
        }
    }
}
