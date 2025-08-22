package org.carball.aden.model.analysis;

import org.carball.aden.model.query.QueryPattern;

import java.util.List;
import java.util.Map;

public record AnalysisResult (
    List<DenormalizationCandidate> denormalizationCandidates,
    Map<String, EntityUsageProfile> usageProfiles,
    List<QueryPattern> queryPatterns
) {}