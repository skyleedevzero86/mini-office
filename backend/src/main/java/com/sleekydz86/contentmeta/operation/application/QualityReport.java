package com.sleekydz86.contentmeta.operation.application;

import com.sleekydz86.contentmeta.operation.infrastructure.QualityResultRow;
import java.util.List;

public record QualityReport(BatchOutcome outcome, int failedRules, List<QualityResultRow> rules) {
}
