package org.carball.aden.model.query;

import java.util.Date;
import java.util.List;

/**
 * Root analysis result from Query Store.
 */

public record QueryStoreAnalysis(
        String database,
        String analysisType,
        Date timestamp,
        int totalQueriesAnalyzed,
        List<AnalyzedQuery> queries,
        QualifiedMetrics qualifiedMetrics
) {}