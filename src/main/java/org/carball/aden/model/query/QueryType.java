package org.carball.aden.model.query;

public enum QueryType {
    SINGLE_ENTITY,
    COLLECTION,
    COMPLEX_JOIN,
    FILTERED_SINGLE,
    FILTERED_COLLECTION,
    EAGER_LOADING,
    COMPLEX_EAGER_LOADING,
    
    // Enhanced query types for better pattern analysis
    WHERE_CLAUSE,
    ORDER_BY,
    GROUP_BY,
    AGGREGATION,
    PAGINATION,
    RANGE_QUERY,
    EXACT_MATCH,
    PARTIAL_MATCH,
    MULTI_COLUMN_FILTER,
    PARAMETER_QUERY,
    COMPLEX_CONDITIONAL
}