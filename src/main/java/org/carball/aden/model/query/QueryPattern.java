package org.carball.aden.model.query;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Data
public class QueryPattern {
    private String type;
    private String targetEntity;
    private int frequency;
    private List<String> sourceFiles = new ArrayList<>();
    private String pattern;
    private QueryType queryType;
    
    // Enhanced query information
    private Set<String> whereClauseColumns = new HashSet<>();
    private Set<String> orderByColumns = new HashSet<>();
    private Set<String> joinedEntities = new HashSet<>();
    private List<FilterCondition> filterConditions = new ArrayList<>();
    private boolean hasComplexWhere = false;
    private boolean hasAggregation = false;
    private boolean hasPagination = false;
    private List<String> parameterTypes = new ArrayList<>();

    public QueryPattern(String type, String targetEntity, int frequency, String sourceFile) {
        this.type = type;
        this.targetEntity = targetEntity;
        this.frequency = frequency;
        this.sourceFiles.add(sourceFile);
    }

    public void incrementFrequency() {
        frequency++;
    }

    public void addSourceFile(String file) {
        if (!sourceFiles.contains(file)) {
            sourceFiles.add(file);
        }
    }
    
    public void addWhereClauseColumn(String column) {
        whereClauseColumns.add(column);
    }
    
    public void addOrderByColumn(String column) {
        orderByColumns.add(column);
    }
    
    public void addJoinedEntity(String entity) {
        joinedEntities.add(entity);
    }
    
    public void addFilterCondition(FilterCondition condition) {
        filterConditions.add(condition);
    }
    
    public void addParameterType(String parameterType) {
        if (!parameterTypes.contains(parameterType)) {
            parameterTypes.add(parameterType);
        }
    }
    
    public boolean hasWhereClause() {
        return !whereClauseColumns.isEmpty() || !filterConditions.isEmpty();
    }
    
    public boolean hasOrderBy() {
        return !orderByColumns.isEmpty();
    }
    
    public boolean hasJoins() {
        return !joinedEntities.isEmpty();
    }
}