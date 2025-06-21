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
}