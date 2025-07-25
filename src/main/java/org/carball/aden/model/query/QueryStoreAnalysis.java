package org.carball.aden.model.query;

import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * Root analysis result from Query Store.
 */
@Data
public class QueryStoreAnalysis {
    private String database;
    private String analysisType;
    private Date timestamp;
    private int totalQueriesAnalyzed;
    private List<AnalyzedQuery> queries;
    private QualifiedMetrics qualifiedMetrics;
}