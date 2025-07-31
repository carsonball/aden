package org.carball.aden.model.recommendation;

import lombok.Data;
import org.carball.aden.model.analysis.NoSQLTarget;
import java.util.List;

@Data
public class NoSQLRecommendation {
    private String primaryEntity;
    private NoSQLTarget targetService;
    private String tableName;
    private KeyStrategy partitionKey;
    private KeyStrategy sortKey;
    private List<GSIStrategy> globalSecondaryIndexes;
    private String estimatedCostSaving;
    private String migrationEffort;
    private String schemaDesign;
    private DesignRationale designRationale;
}