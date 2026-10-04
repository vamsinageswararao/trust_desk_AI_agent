package com.trustdesk.agent;

import java.util.Set;

/** @param trustedAudiences lower-case audience names whose documents may be used as evidence */
public record EngineSettings(double minEvidenceScore, int retrievalTopK, Set<String> trustedAudiences) {}
