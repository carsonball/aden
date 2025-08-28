package org.carball.aden.model.query;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Set;

@Data
@Builder
public class FilterCondition {
    private String column;
    private String operator;
    private String valueType;
    private List<String> sampleValues;
    private boolean isParameter;
    private String parameterName;
    
    /**
     * Valid filter operators found in LINQ queries
     */
    public static final Set<String> VALID_OPERATORS = Set.of(
        "==", "!=", ">=", "<=", ">", "<", 
        "CONTAINS", "STARTS_WITH", "ENDS_WITH",
        "IN", "NOT_IN", "LIKE", "IS_NULL", "IS_NOT_NULL", 
        "BETWEEN", "ANY", "ALL"
    );
    
    /**
     * Valid value types that can be used in filters
     */
    public static final Set<String> VALID_VALUE_TYPES = Set.of(
        "STRING", "INTEGER", "DECIMAL", "BOOLEAN", 
        "DATE", "DATETIME", "GUID", "ENUM", 
        "PARAMETER", "COLLECTION", "UNKNOWN"
    );
}