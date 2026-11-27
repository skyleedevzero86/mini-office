package com.sleekydz86.contentmeta.dashboard.application;

import com.sleekydz86.contentmeta.operation.infrastructure.QualityResultRow;
import java.util.List;

public record QualityDetail(long jobRunId, List<QualityResultRow> rules) {
}
