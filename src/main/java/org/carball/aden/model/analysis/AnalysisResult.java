package org.carball.aden.model.analysis;

import lombok.Data;
import org.carball.aden.model.query.QueryPattern;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class AnalysisResult {
    private List<DenormalizationCandidate> denormalizationCandidates;
    private Map<String, EntityUsageProfile> usageProfiles = new HashMap<>();
    private ComplexityAnalysis complexityAnalysis;
    private List<QueryPattern> queryPatterns;
}