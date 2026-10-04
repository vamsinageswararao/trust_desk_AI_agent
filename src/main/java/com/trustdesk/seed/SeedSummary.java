package com.trustdesk.seed;

/** Result of one seed run. {@code ticketsInserted}/{@code documentsWritten} are 0 on an idempotent re-run. */
public record SeedSummary(int customers, int orders, int tickets, int ticketsInserted, int toolActions,
                          int evalCases, int documents, int documentsWritten) {}
