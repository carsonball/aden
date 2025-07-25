package org.carball.aden.model.query;

import lombok.Data;
import java.util.List;

/**
 * Table access patterns from Query Store analysis.
 */
@Data
public class TableAccessPatterns {
    private List<TableCombination> frequentTableCombinations;
    private boolean hasStrongCoAccessPatterns;
}