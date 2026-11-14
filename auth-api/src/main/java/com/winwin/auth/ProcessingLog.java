package com.winwin.auth;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processing_log")
public class ProcessingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "input_text", nullable = false)
    private String inputText;

    @Column(name = "output_text", nullable = false)
    private String outputText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProcessingLog() {}

    public ProcessingLog(UUID userId, String inputText, String outputText) {
        this.userId = userId;
        this.inputText = inputText;
        this.outputText = outputText;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getInputText() { return inputText; }
    public String getOutputText() { return outputText; }
    public Instant getCreatedAt() { return createdAt; }
}