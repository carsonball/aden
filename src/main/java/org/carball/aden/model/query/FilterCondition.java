package org.carball.aden.model.query;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class FilterCondition {
    private String column;
    private String operator;
    private String valueType;
    private List<String> sampleValues;
    private boolean isParameter;
    private String parameterName;
    private int frequency;
    
    /**
     * Common filter operators found in LINQ queries
     */
    public enum FilterOperator {
        EQUALS,
        NOT_EQUALS, 
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        CONTAINS,
        STARTS_WITH,
        ENDS_WITH,
        IN,
        NOT_IN,
        LIKE,
        IS_NULL,
        IS_NOT_NULL,
        BETWEEN,
        ANY,
        ALL
    }
    
    /**
     * Value types that can be used in filters
     */
    public enum ValueType {
        STRING,
        INTEGER,
        DECIMAL,
        BOOLEAN,
        DATE,
        DATETIME,
        GUID,
        ENUM,
        PARAMETER,
        COLLECTION
    }
}