package os.db.evolve;

class StatementParser {

    private final StringBuilder statement = new StringBuilder();
    private boolean statementComplete = false;
    private boolean newLineEndDelimiterEnabled = false;
    private int startLineNumber = -1;

    void accept(String line, int lineNumber) {
        if (line == null) {
            if (statement.length() > 0) {
                statementComplete = true;
            }
            return;
        }

        if (line.startsWith("--")) { // skip single line comments
            if (line.contains("###_NEW_LINE_END_DELIMITER_ON_###")) {
                newLineEndDelimiterEnabled = true;
            }
            return;
        }

        if (newLineEndDelimiterEnabled && line.isBlank()) {
            statementComplete = true;
            return;
        }

        if (line.isBlank()) {
            return;
        }

        if (startLineNumber == -1) {
            startLineNumber = lineNumber;
        }

        statement.append(line.trim()).append("\n");

        if(!newLineEndDelimiterEnabled && line.endsWith(";")) {
            statementComplete = true;
        }
    }

    boolean isComplete() {
        return statementComplete;
    }

    String getStatement() {
        return statement.toString().trim();
    }

    int getStartLineNumber() {
        return startLineNumber;
    }

    void reset() {
        statement.setLength(0);
        statementComplete = false;
        newLineEndDelimiterEnabled = false;
        startLineNumber = -1;
    }
}
