package com.trustdesk.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.trustdesk.seed.SeedRecords.SeedBundle;
import com.trustdesk.seed.SeedRecords.SeedCustomer;
import com.trustdesk.seed.SeedRecords.SeedDocument;
import com.trustdesk.seed.SeedRecords.SeedEvalCase;
import com.trustdesk.seed.SeedRecords.SeedOrder;
import com.trustdesk.seed.SeedRecords.SeedTicket;
import com.trustdesk.seed.SeedRecords.SeedToolAction;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads the provided seed files read-only and validates referential integrity (same rules as
 * scripts/validate_pack.py). Never writes to the data directory.
 */
public class SeedDataReader {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) // ignores the expected_* evaluation labels
            .build();

    private final Path dataDir;

    public SeedDataReader(Path dataDir) {
        this.dataDir = dataDir;
    }

    public SeedBundle read() {
        if (!Files.isDirectory(dataDir)) {
            throw new SeedDataException("Seed data directory not found: " + dataDir.toAbsolutePath());
        }
        SeedBundle bundle = new SeedBundle(
                readJson("customers.json", new TypeReference<List<SeedCustomer>>() {}),
                readJson("orders.json", new TypeReference<List<SeedOrder>>() {}),
                readJson("tickets.json", new TypeReference<List<SeedTicket>>() {}),
                readJson("tool_actions.json", new TypeReference<List<SeedToolAction>>() {}),
                readEvalCases(),
                readDocuments());
        validate(bundle);
        return bundle;
    }

    private <T> T readJson(String fileName, TypeReference<T> type) {
        Path file = dataDir.resolve(fileName);
        try {
            return MAPPER.readValue(file.toFile(), type);
        } catch (IOException e) {
            throw new SeedDataException("Cannot read seed file " + file, e);
        }
    }

    private List<SeedEvalCase> readEvalCases() {
        Path file = dataDir.resolve("eval_cases.jsonl");
        List<SeedEvalCase> cases = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    cases.add(MAPPER.readValue(line, SeedEvalCase.class));
                }
            }
        } catch (IOException e) {
            throw new SeedDataException("Cannot read seed file " + file, e);
        }
        return cases;
    }

    private List<SeedDocument> readDocuments() {
        Path kbDir = dataDir.resolve("knowledge_base");
        if (!Files.isDirectory(kbDir)) {
            throw new SeedDataException("Knowledge base directory not found: " + kbDir.toAbsolutePath());
        }
        String root = dataDir.toAbsolutePath().normalize().getFileName() + "/knowledge_base/";
        try (Stream<Path> files = Files.list(kbDir)) {
            List<Path> markdown = files.filter(p -> p.getFileName().toString().endsWith(".md")).sorted().toList();
            List<SeedDocument> documents = new ArrayList<>();
            for (Path file : markdown) {
                documents.add(parseDocument(file, root + file.getFileName()));
            }
            return documents;
        } catch (IOException e) {
            throw new SeedDataException("Cannot list knowledge base " + kbDir, e);
        }
    }

    static SeedDocument parseDocument(Path file, String sourcePath) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SeedDataException("Cannot read knowledge document " + file, e);
        }
        String docId = null;
        String title = null;
        String version = null;
        String audience = null;
        for (String line : content.split("\\R")) {
            String lower = line.toLowerCase();
            if (title == null && line.startsWith("# ")) {
                title = line.substring(2).strip();
            } else if (docId == null && lower.startsWith("doc id:")) {
                docId = valueAfterColon(line);
            } else if (version == null && lower.startsWith("version:")) {
                version = valueAfterColon(line);
            } else if (audience == null && lower.startsWith("audience:")) {
                audience = valueAfterColon(line);
            }
        }
        if (docId == null || docId.isBlank()) {
            throw new SeedDataException(file + " is missing a 'Doc ID:' line");
        }
        return new SeedDocument(docId, title != null ? title : file.getFileName().toString(), sourcePath,
                version, audience, content, sha256(content));
    }

    private static String valueAfterColon(String line) {
        return line.substring(line.indexOf(':') + 1).strip();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static void validate(SeedBundle b) {
        Set<String> customerIds = unique(b.customers().stream().map(SeedCustomer::customerId), "customer_id");
        Set<String> orderIds = unique(b.orders().stream().map(SeedOrder::orderId), "order_id");
        Set<String> ticketIds = unique(b.tickets().stream().map(SeedTicket::ticketId), "ticket_id");
        Set<String> toolNames = unique(b.toolActions().stream().map(SeedToolAction::toolName), "tool_name");
        unique(b.evalCases().stream().map(SeedEvalCase::caseId), "case_id");
        Set<String> docIds = unique(b.documents().stream().map(SeedDocument::docId), "doc_id");
        if (docIds.isEmpty()) {
            throw new SeedDataException("No knowledge documents found");
        }

        for (SeedOrder o : b.orders()) {
            require(customerIds, o.customerId(), "Order " + o.orderId() + " has unknown customer_id");
        }
        for (SeedTicket t : b.tickets()) {
            require(customerIds, t.customerId(), "Ticket " + t.ticketId() + " has unknown customer_id");
            if (t.orderId() != null) {
                require(orderIds, t.orderId(), "Ticket " + t.ticketId() + " has unknown order_id");
            }
        }
        for (SeedEvalCase c : b.evalCases()) {
            String prefix = "Eval case " + c.caseId();
            require(ticketIds, c.ticketId(), prefix + " has unknown ticket_id");
            Map<String, Object> expected = c.expected() == null ? Map.of() : c.expected();
            for (String docId : strings(expected, "must_cite_doc_ids")) {
                require(docIds, docId, prefix + " references unknown doc id");
            }
            for (String key : List.of("allowed_actions", "disallowed_actions")) {
                for (String tool : strings(expected, key)) {
                    require(toolNames, tool, prefix + " references unknown action");
                }
            }
        }
    }

    private static Set<String> unique(Stream<String> ids, String what) {
        Set<String> seen = new LinkedHashSet<>();
        ids.forEach(id -> {
            if (id == null || id.isBlank()) {
                throw new SeedDataException("Blank " + what + " in seed data");
            }
            if (!seen.add(id)) {
                throw new SeedDataException("Duplicate " + what + ": " + id);
            }
        });
        return seen;
    }

    private static void require(Set<String> known, String id, String message) {
        if (!known.contains(id)) {
            throw new SeedDataException(message + ": " + id);
        }
    }

    private static List<String> strings(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
