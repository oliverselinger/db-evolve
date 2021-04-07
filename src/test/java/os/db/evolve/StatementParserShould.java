package os.db.evolve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StatementParserShould {

    @Test
    void return_statement_delimited_by_semicolon() {
        StatementParser parser = new StatementParser();

        String line1 = "INSERT INTO TEST";
        String line2 = "VALUES (1);";

        parser.accept(line1, 1);
        parser.accept(line2, 2);

        assertTrue(parser.isComplete());
        assertEquals(line1 + "\n" + truncateSemicolon(line2), parser.getStatement());
        assertEquals(1, parser.getStartLineNumber());
    }

    @Test
    void be_reusable_to_parse_multiple_stmts() {
        StatementParser parser = new StatementParser();

        String line1 = "INSERT INTO TEST";
        String line2 = "VALUES (1);";

        parser.accept(line1, 1);
        parser.accept(line2, 2);

        assertTrue(parser.isComplete());
        assertEquals(line1 + "\n" + truncateSemicolon(line2), parser.getStatement());
        assertEquals(1, parser.getStartLineNumber());

        parser.reset();

        String line3 = "INSERT INTO TEST VALUES (2);";
        parser.accept(line3, 3);

        assertTrue(parser.isComplete());
        assertEquals(truncateSemicolon(line3), parser.getStatement());
        assertEquals(3, parser.getStartLineNumber());
    }

    @Test
    void ignore_single_line_comment() {
        StatementParser parser = new StatementParser();

        String line1 = "-- INSERT INTO TEST VALUES (1);";
        String line2 = "INSERT INTO TEST VALUES (2);";

        parser.accept(line1, 1);
        parser.accept(line2, 2);
        parser.accept(null, 3);

        assertTrue(parser.isComplete());
        assertEquals(truncateSemicolon(line2), parser.getStatement());
        assertEquals(2, parser.getStartLineNumber());
    }

    @Test
    void ignore_blank_lines() {
        StatementParser parser = new StatementParser();

        String line1 = " ";
        String line2 = "INSERT INTO TEST VALUES (2);";

        parser.accept(line1, 1);
        parser.accept(line2, 2);

        assertTrue(parser.isComplete());
        assertEquals(truncateSemicolon(line2), parser.getStatement());
        assertEquals(2, parser.getStartLineNumber());
    }

    @Test
    void ignore_null_line_when_no_statement_got_parsed() {
        StatementParser parser = new StatementParser();

        parser.accept(null, 1);

        assertFalse(parser.isComplete());
    }

    @Test
    void return_statement_delimited_by_new_line() {
        StatementParser parser = new StatementParser();

        String line1 = "-- ###_NEW_LINE_END_DELIMITER_ON_###";
        String line2 = "BEGIN";
        String line3 = "dbms_output.put_line (‘Hello World..');";
        String line4 = "END;";
        String line5 = "/";
        String line6 = "";

        parser.accept(line1, 1);
        parser.accept(line2, 2);
        parser.accept(line3, 3);
        parser.accept(line4, 4);
        parser.accept(line5, 5);
        parser.accept(line6, 6);

        assertTrue(parser.isComplete());
        assertEquals(line2 + "\n" + line3 + "\n" + line4 + "\n" + line5, parser.getStatement());
        assertEquals(2, parser.getStartLineNumber());
    }

    @Test
    void use_null_line_as_delimiter_in_case_new_line_delimiter_is_used() {
        StatementParser parser = new StatementParser();

        String line1 = "-- ###_NEW_LINE_END_DELIMITER_ON_###";
        String line2 = "BEGIN";
        String line3 = "dbms_output.put_line (‘Hello World..');";
        String line4 = "END;";
        String line5 = "/";
        String line6 = null;

        parser.accept(line1, 1);
        parser.accept(line2, 2);
        parser.accept(line3, 3);
        parser.accept(line4, 4);
        parser.accept(line5, 5);
        parser.accept(line6, 6);

        assertTrue(parser.isComplete());
        assertEquals(line2 + "\n" + line3 + "\n" + line4 + "\n" + line5, parser.getStatement());
        assertEquals(2, parser.getStartLineNumber());
    }

    @Test
    void return_statements_with_mixed_delimiters() {
        StatementParser parser = new StatementParser();

        String line1 = "INSERT INTO TEST VALUES (1);";
        String line2 = "";
        String line3 = "-- ###_NEW_LINE_END_DELIMITER_ON_###";
        String line4 = "BEGIN";
        String line5 = "dbms_output.put_line (‘Hello World..');";
        String line6 = "END;";
        String line7 = "/";
        String line8 = "";
        String line9 = "INSERT INTO TEST VALUES (2);";

        parser.accept(line1, 1);

        assertTrue(parser.isComplete());
        assertEquals(truncateSemicolon(line1), parser.getStatement());
        assertEquals(1, parser.getStartLineNumber());

        parser.reset();

        parser.accept(line2, 2);
        parser.accept(line3, 3);
        parser.accept(line4, 4);
        parser.accept(line5, 5);
        parser.accept(line6, 6);
        parser.accept(line7, 7);
        parser.accept(line8, 8);

        assertTrue(parser.isComplete());
        assertEquals(line4 + "\n" + line5 + "\n" + line6 + "\n" + line7, parser.getStatement());
        assertEquals(4, parser.getStartLineNumber());

        parser.reset();

        parser.accept(line9, 9);

        assertTrue(parser.isComplete());
        assertEquals(truncateSemicolon(line9), parser.getStatement());
        assertEquals(9, parser.getStartLineNumber());
    }

    private String truncateSemicolon(String line) {
        return line.substring(0, line.length() - 1);
    }
}
