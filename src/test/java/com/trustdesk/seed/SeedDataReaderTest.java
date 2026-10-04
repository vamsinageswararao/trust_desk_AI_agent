package com.trustdesk.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trustdesk.seed.SeedRecords.SeedBundle;
import com.trustdesk.seed.SeedRecords.SeedTicket;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pure unit tests (no Spring) for reading and validating the seed pack. Mutations happen on temp copies only. */
class SeedDataReaderTest {

    private static final Path REAL_DATA = Path.of("data");

    @TempDir Path tmp;

    @Test
    void readsProvidedPack() {
        SeedBundle b = new SeedDataReader(REAL_DATA).read();
        assertThat(b.customers()).hasSize(6);
        assertThat(b.orders()).hasSize(6);
        assertThat(b.tickets()).hasSize(8);
        assertThat(b.toolActions()).hasSize(6);
        assertThat(b.evalCases()).hasSize(8);
        assertThat(b.documents()).hasSize(8);
    }

    @Test
    void seedTicketDoesNotCarryEvaluationLabels() {
        assertThat(Arrays.stream(SeedTicket.class.getRecordComponents()).map(RecordComponent::getName))
                .noneMatch(name -> name.startsWith("expected"));
    }

    @Test
    void rejectsMissingDirectory() {
        assertThatThrownBy(() -> new SeedDataReader(tmp.resolve("nope")).read())
                .isInstanceOf(SeedDataException.class);
    }

    @Test
    void rejectsDocumentWithoutDocId() throws IOException {
        Path data = copyData();
        edit(data.resolve("knowledge_base/refund_policy.md"), "Doc ID: KB-REFUND-001", "Document: none");
        assertThatThrownBy(() -> new SeedDataReader(data).read())
                .isInstanceOf(SeedDataException.class).hasMessageContaining("Doc ID");
    }

    @Test
    void rejectsDuplicateDocumentIds() throws IOException {
        Path data = copyData();
        Files.copy(data.resolve("knowledge_base/refund_policy.md"), data.resolve("knowledge_base/refund_copy.md"));
        assertThatThrownBy(() -> new SeedDataReader(data).read())
                .isInstanceOf(SeedDataException.class).hasMessageContaining("Duplicate doc_id");
    }

    @Test
    void rejectsTicketWithUnknownCustomer() throws IOException {
        Path data = copyData();
        edit(data.resolve("tickets.json"), "cus_1001", "cus_9999");
        assertThatThrownBy(() -> new SeedDataReader(data).read())
                .isInstanceOf(SeedDataException.class).hasMessageContaining("cus_9999");
    }

    @Test
    void rejectsEvalCaseWithUnknownDocId() throws IOException {
        Path data = copyData();
        edit(data.resolve("eval_cases.jsonl"), "KB-SECURITY-001", "KB-NOPE-001");
        assertThatThrownBy(() -> new SeedDataReader(data).read())
                .isInstanceOf(SeedDataException.class).hasMessageContaining("KB-NOPE-001");
    }

    @Test
    void rejectsEvalCaseWithUnknownAction() throws IOException {
        Path data = copyData();
        edit(data.resolve("eval_cases.jsonl"), "issue_coupon", "nuke_everything");
        assertThatThrownBy(() -> new SeedDataReader(data).read())
                .isInstanceOf(SeedDataException.class).hasMessageContaining("nuke_everything");
    }

    private Path copyData() throws IOException {
        Path target = tmp.resolve("data");
        try (Stream<Path> walk = Files.walk(REAL_DATA)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path dest = target.resolve(REAL_DATA.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(p, dest);
                }
            }
        }
        return target;
    }

    private static void edit(Path file, String from, String to) throws IOException {
        String content = Files.readString(file);
        assertThat(content).contains(from);
        Files.writeString(file, content.replace(from, to));
    }
}
