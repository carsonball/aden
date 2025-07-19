package org.carball.aden.model.recommendation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * Structured response format for AI recommendations.
 * This class defines the exact JSON structure expected from the AI API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationResponse {
    
    @JsonProperty("recommendations")
    private List<EntityRecommendation> recommendations;
    
    @JsonProperty("overallStrategy")
    private OverallStrategy overallStrategy;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EntityRecommendation {
        @JsonProperty("entityName")
        private String entityName;
        
        @JsonProperty("targetService")
        private String targetService; // "DynamoDB", "DocumentDB", "Neptune"
        
        @JsonProperty("justification")
        private String justification;
        
        @JsonProperty("tableName")
        private String tableName;
        
        @JsonProperty("partitionKey")
        private KeyDefinition partitionKey;
        
        @JsonProperty("sortKey")
        private KeyDefinition sortKey;
        
        @JsonProperty("globalSecondaryIndexes")
        private List<GSIDefinition> globalSecondaryIndexes;
        
        @JsonProperty("accessPatterns")
        private List<AccessPattern> accessPatterns;
        
        @JsonProperty("estimatedCostSaving")
        private CostEstimate costEstimate;
        
        @JsonProperty("migrationEffort")
        private MigrationEffort migrationEffort;
        
        @JsonProperty("singleTableDesign")
        private SingleTableDesign singleTableDesign;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KeyDefinition {
        @JsonProperty("attributeName")
        private String attributeName;
        
        @JsonProperty("attributeType")
        private String attributeType; // "S", "N", "B"
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("exampleValues")
        private List<String> exampleValues;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GSIDefinition {
        @JsonProperty("indexName")
        private String indexName;
        
        @JsonProperty("partitionKey")
        private String partitionKey;
        
        @JsonProperty("sortKey")
        private String sortKey;
        
        @JsonProperty("purpose")
        private String purpose;
        
        @JsonProperty("projectionType")
        private String projectionType; // "ALL", "KEYS_ONLY", "INCLUDE"
        
        @JsonProperty("projectedAttributes")
        private List<String> projectedAttributes;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AccessPattern {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("keyCondition")
        private String keyCondition;
        
        @JsonProperty("filterExpression")
        private String filterExpression;
        
        @JsonProperty("indexUsed")
        private String indexUsed; // null for base table
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CostEstimate {
        @JsonProperty("percentageSaving")
        private Integer percentageSaving; // e.g., 60 for 60%
        
        @JsonProperty("explanation")
        private String explanation;
        
        @JsonProperty("monthlyEstimateUSD")
        private Double monthlyEstimateUSD;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MigrationEffort {
        @JsonProperty("estimatedWeeks")
        private Integer estimatedWeeks;
        
        @JsonProperty("complexity")
        private String complexity; // "LOW", "MEDIUM", "HIGH"
        
        @JsonProperty("mainChallenges")
        private List<String> mainChallenges;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SingleTableDesign {
        @JsonProperty("isRecommended")
        private Boolean isRecommended;
        
        @JsonProperty("sharedTableName")
        private String sharedTableName;
        
        @JsonProperty("entityDiscriminator")
        private String entityDiscriminator;
        
        @JsonProperty("explanation")
        private String explanation;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OverallStrategy {
        @JsonProperty("primaryApproach")
        private String primaryApproach;
        
        @JsonProperty("keyConsiderations")
        private List<String> keyConsiderations;
        
        @JsonProperty("totalEstimatedSaving")
        private Integer totalEstimatedSaving;
        
        @JsonProperty("totalMigrationWeeks")
        private Integer totalMigrationWeeks;
    }
}