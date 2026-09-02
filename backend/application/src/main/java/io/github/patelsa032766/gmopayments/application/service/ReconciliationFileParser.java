package io.github.patelsa032766.gmopayments.application.service;

import io.github.patelsa032766.gmopayments.domain.ReconciliationRecord;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the documented, intentionally small reconciliation interchange.
 *
 * <p>CSV and TSV are accepted. Required headers are {@code orderId} and
 * {@code status}; {@code amountJpy} and {@code eventOccurredAt} are optional.
 * Unknown columns are ignored so a provider cannot turn the audit database
 * into arbitrary payload storage.</p>
 */
public final class ReconciliationFileParser {
    private static final int MAX_ROWS = 100_000;

    public List<ReconciliationRecord> parse(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty()) throw new IllegalArgumentException("Reconciliation file is empty");
        if (lines.size() - 1 > MAX_ROWS) throw new IllegalArgumentException("Reconciliation file exceeds row limit");

        char delimiter = lines.getFirst().contains("\t") ? '\t' : ',';
        List<String> headers = parseDelimitedLine(lines.getFirst(), delimiter).stream()
                .map(ReconciliationFileParser::normalizeHeader).toList();
        int orderIndex = requiredIndex(headers, "orderid");
        int statusIndex = requiredIndex(headers, "status");
        int amountIndex = optionalIndex(headers, "amountjpy");
        int occurredIndex = optionalIndex(headers, "eventoccurredat");

        List<ReconciliationRecord> records = new ArrayList<>();
        for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            int rowNumber = lineIndex + 1;
            List<String> cells = parseDelimitedLine(lines.get(lineIndex), delimiter);
            if (cells.size() != headers.size()) {
                throw new IllegalArgumentException("Row " + rowNumber + " has " + cells.size()
                        + " fields; expected " + headers.size());
            }
            String orderId = requiredCell(cells, orderIndex, "orderId", rowNumber, 128);
            String status = requiredCell(cells, statusIndex, "status", rowNumber, 64)
                    .toUpperCase(Locale.ROOT);
            Long amount = amountIndex < 0 || cells.get(amountIndex).isBlank()
                    ? null : parseAmount(cells.get(amountIndex), rowNumber);
            Instant occurredAt = occurredIndex < 0 || cells.get(occurredIndex).isBlank()
                    ? null : parseInstant(cells.get(occurredIndex), rowNumber);

            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("orderId", orderId);
            safe.put("status", status);
            if (amount != null) safe.put("amountJpy", amount);
            if (occurredAt != null) safe.put("eventOccurredAt", occurredAt.toString());
            records.add(new ReconciliationRecord(rowNumber, orderId, status, amount, occurredAt, safe));
        }
        return List.copyOf(records);
    }

    static List<String> parseDelimitedLine(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char value = line.charAt(i);
            if (value == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (value == delimiter && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(value);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unterminated quoted field");
        cells.add(current.toString().trim());
        return cells;
    }

    private static int requiredIndex(List<String> headers, String name) {
        int index = optionalIndex(headers, name);
        if (index < 0) throw new IllegalArgumentException("Required reconciliation header is missing: " + name);
        return index;
    }

    private static int optionalIndex(List<String> headers, String name) {
        return headers.indexOf(name);
    }

    private static String normalizeHeader(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static String requiredCell(List<String> cells, int index, String name, int row, int maxLength) {
        String value = cells.get(index).trim();
        if (value.isEmpty()) throw new IllegalArgumentException(name + " is blank on row " + row);
        if (value.length() > maxLength) throw new IllegalArgumentException(name + " is too long on row " + row);
        if (value.indexOf('\0') >= 0) throw new IllegalArgumentException(name + " contains a null byte on row " + row);
        return value;
    }

    private static long parseAmount(String value, int row) {
        try {
            long amount = Long.parseLong(value.trim());
            if (amount < 0) throw new NumberFormatException("negative");
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("amountJpy is invalid on row " + row, exception);
        }
    }

    private static Instant parseInstant(String value, int row) {
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("eventOccurredAt must be ISO-8601 UTC on row " + row, exception);
        }
    }
}
