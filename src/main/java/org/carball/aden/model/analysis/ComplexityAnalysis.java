package org.carball.aden.model.analysis;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class ComplexityAnalysis {
    private Map<String, Integer> entityComplexityScores;
    private int overallComplexity;
    private String complexityReason;
}