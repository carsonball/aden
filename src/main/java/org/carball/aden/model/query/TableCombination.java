package org.carball.aden.model.query;

import lombok.Data;
import java.util.List;

/**
 * Represents tables that are frequently accessed together.
 */
@Data
public class TableCombination {
    private List<String> tables;
    private long totalExecutions;
    private double executionPercentage;
}