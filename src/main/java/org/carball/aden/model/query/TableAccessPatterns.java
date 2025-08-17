package org.carball.aden.model.query;

import java.util.List;

/**
 * Table access patterns from Query Store analysis.
 */
public record TableAccessPatterns (
    List<TableCombination> frequentTableCombinations,
    boolean hasStrongCoAccessPatterns
){}