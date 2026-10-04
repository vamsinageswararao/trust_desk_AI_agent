package com.trustdesk.api.dto;

import java.util.List;

/** Optional body: run only these case ids (default: all cases). */
public record EvalRunRequest(List<String> caseIds) {}
