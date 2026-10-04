package com.trustdesk.domain;

import com.trustdesk.domain.converter.InstantConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Knowledge-base document. {@code docId} (e.g. KB-REFUND-001) is the primary key and is never rewritten. */
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {
    @Id @Column(length = 64)
    private String docId;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private String sourcePath;
    @Column(length = 32) private String version;
    private String audience;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(nullable = false, length = 64) private String checksum;
    @Convert(converter = InstantConverter.class) @Column(nullable = false, length = 40)
    private Instant ingestedAt;

    protected KnowledgeDocument() {}

    public KnowledgeDocument(String docId, String title, String sourcePath, String version, String audience,
                             String content, String checksum) {
        this.docId = docId;
        this.title = title;
        this.sourcePath = sourcePath;
        this.version = version;
        this.audience = audience;
        this.content = content;
        this.checksum = checksum;
        this.ingestedAt = Instant.now();
    }

    public String getDocId() { return docId; }
    public String getTitle() { return title; }
    public String getSourcePath() { return sourcePath; }
    public String getVersion() { return version; }
    public String getAudience() { return audience; }
    public String getContent() { return content; }
    public String getChecksum() { return checksum; }
    public Instant getIngestedAt() { return ingestedAt; }
}
