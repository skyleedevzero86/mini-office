package com.sleekydz86.contentmeta.dashboard.application;

import com.sleekydz86.contentmeta.migration.domain.VerifyTableRow;
import com.sleekydz86.contentmeta.migration.infrastructure.LatestVerifyRow;
import java.util.List;

public record VerifyDetail(LatestVerifyRow latest, List<VerifyTableRow> tables) {
}
