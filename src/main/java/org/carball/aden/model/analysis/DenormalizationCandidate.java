package org.carball.aden.model.analysis;

import lombok.Data;
import java.util.List;

@Data
public class DenormalizationCandidate {
    private String primaryEntity;
    private List<String> relatedEntities;
    private MigrationComplexity complexity;
    private String reason;
    private NoSQLTarget recommendedTarget;
    private int score;

    public String getScoreInterpretation() {
        if (score >= 150) return "Excellent candidate - migrate immediately";
        if (score >= 100) return "Strong candidate - high priority";
        if (score >= 60) return "Good candidate - medium priority";
        if (score >= 30) return "Fair candidate - low priority";
        return "Poor candidate - reconsider approach";
    }
}