package org.carball.aden.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.carball.aden.model.analysis.*;
import org.carball.aden.model.recommendation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class JsonRecommendationEngineTest {

    private JsonRecommendationEngine engine;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        System.setProperty("skip.ai", "true");
        engine = new JsonRecommendationEngine("test-key");
        objectMapper = new ObjectMapper();
    }

    @Test
    public void shouldGenerateFallbackRecommendationsWhenAISkipped() {
        // Given
        AnalysisResult analysis = createSampleAnalysis();

        // When
        List<NoSQLRecommendation> recommendations = engine.generateRecommendations(analysis);

        // Then
        assertEquals(2, recommendations.size());
        
        NoSQLRecommendation customerRec = recommendations.stream()
                .filter(r -> r.getPrimaryEntity().equals("Customer"))
                .findFirst().orElse(null);
        
        assertNotNull(customerRec);
        assertEquals(NoSQLTarget.DYNAMODB, customerRec.getTargetService());
        assertEquals("CustomerTable", customerRec.getTableName());
        assertNotNull(customerRec.getPartitionKey());
        assertEquals("customerId", customerRec.getPartitionKey().getAttribute());
    }

    @Test
    public void shouldParseValidJsonResponse() throws Exception {
        // Given
        String validJson = """
            {
              "recommendations": [
                {
                  "entityName": "Customer",
                  "targetService": "DynamoDB",
                  "justification": "High-volume key-based access",
                  "tableName": "CustomerData",
                  "partitionKey": {
                    "attributeName": "customerId",
                    "attributeType": "S",
                    "description": "Customer unique identifier",
                    "exampleValues": ["CUST123", "CUST456"]
                  },
                  "sortKey": {
                    "attributeName": "entityType",
                    "attributeType": "S",
                    "description": "Entity type discriminator",
                    "exampleValues": ["PROFILE", "ADDRESS"]
                  },
                  "globalSecondaryIndexes": [
                    {
                      "indexName": "EmailIndex",
                      "partitionKey": "email",
                      "sortKey": "customerId",
                      "purpose": "Query by email",
                      "projectionType": "ALL"
                    }
                  ],
                  "accessPatterns": [
                    {
                      "name": "Get customer by ID",
                      "description": "Retrieve customer profile",
                      "keyCondition": "customerId = :id",
                      "indexUsed": null
                    }
                  ],
                  "estimatedCostSaving": {
                    "percentageSaving": 60,
                    "explanation": "Reduced licensing costs",
                    "monthlyEstimateUSD": 2500.0
                  },
                  "migrationEffort": {
                    "estimatedWeeks": 3,
                    "complexity": "MEDIUM",
                    "mainChallenges": ["Data transformation", "Query refactoring"]
                  },
                  "singleTableDesign": {
                    "isRecommended": true,
                    "sharedTableName": "ApplicationData",
                    "entityDiscriminator": "entityType",
                    "explanation": "Customer and Order data frequently accessed together"
                  }
                }
              ],
              "overallStrategy": {
                "primaryApproach": "Single-table design",
                "keyConsiderations": ["High read volume", "Related data access"],
                "totalEstimatedSaving": 60,
                "totalMigrationWeeks": 8
              }
            }
            """;

        // When
        RecommendationResponse response = objectMapper.readValue(validJson, RecommendationResponse.class);

        // Then
        assertNotNull(response);
        assertNotNull(response.getRecommendations());
        assertEquals(1, response.getRecommendations().size());
        
        RecommendationResponse.EntityRecommendation rec = response.getRecommendations().get(0);
        assertEquals("Customer", rec.getEntityName());
        assertEquals("DynamoDB", rec.getTargetService());
        assertEquals("CustomerData", rec.getTableName());
        
        assertNotNull(rec.getPartitionKey());
        assertEquals("customerId", rec.getPartitionKey().getAttributeName());
        assertEquals("S", rec.getPartitionKey().getAttributeType());
        
        assertNotNull(rec.getSortKey());
        assertEquals("entityType", rec.getSortKey().getAttributeName());
        
        assertNotNull(rec.getGlobalSecondaryIndexes());
        assertEquals(1, rec.getGlobalSecondaryIndexes().size());
        assertEquals("EmailIndex", rec.getGlobalSecondaryIndexes().get(0).getIndexName());
        
        assertNotNull(rec.getCostEstimate());
        assertEquals(Integer.valueOf(60), rec.getCostEstimate().getPercentageSaving());
        
        assertNotNull(rec.getMigrationEffort());
        assertEquals(Integer.valueOf(3), rec.getMigrationEffort().getEstimatedWeeks());
        assertEquals("MEDIUM", rec.getMigrationEffort().getComplexity());
        
        assertNotNull(rec.getSingleTableDesign());
        assertTrue(rec.getSingleTableDesign().getIsRecommended());
        assertEquals("ApplicationData", rec.getSingleTableDesign().getSharedTableName());
    }

    @Test
    public void shouldValidateRequiredFields() throws Exception {
        // Given - JSON missing required fields
        String invalidJson = """
            {
              "recommendations": [
                {
                  "entityName": "Customer",
                  "targetService": "DynamoDB"
                }
              ]
            }
            """;

        // When parsing with validation
        RecommendationResponse response = objectMapper.readValue(invalidJson, RecommendationResponse.class);
        
        // The response should parse (Jackson doesn't enforce required)
        assertNotNull(response);
        
        // But our validation should catch missing fields
        try {
            validateResponse(response);
            fail("Should have thrown exception for missing fields");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("missing"));
        }
    }

    @Test
    public void shouldHandleMalformedJson() {
        // Given
        String malformedJson = "{ invalid json }";
        
        // When/Then
        try {
            objectMapper.readValue(malformedJson, RecommendationResponse.class);
            fail("Should have thrown exception for malformed JSON");
        } catch (Exception e) {
            // Expected
        }
    }

    private AnalysisResult createSampleAnalysis() {
        AnalysisResult result = new AnalysisResult();
        
        DenormalizationCandidate customer = DenormalizationCandidate.builder()
                .primaryEntity("Customer")
                .relatedEntities(Arrays.asList("Order", "Address"))
                .complexity(MigrationComplexity.MEDIUM)
                .score(85)
                .reason("High frequency access with related entities")
                .recommendedTarget(NoSQLTarget.DYNAMODB)
                .build();
        
        DenormalizationCandidate product = DenormalizationCandidate.builder()
                .primaryEntity("Product")
                .relatedEntities(List.of("Category"))
                .complexity(MigrationComplexity.LOW)
                .score(65)
                .reason("Simple key-based access")
                .recommendedTarget(NoSQLTarget.DYNAMODB)
                .build();
        
        result.setDenormalizationCandidates(Arrays.asList(customer, product));
        result.setUsageProfiles(new HashMap<>());
        result.setQueryPatterns(new ArrayList<>());
        
        return result;
    }
    
    private void validateResponse(RecommendationResponse response) {
        if (response.getRecommendations() == null || response.getRecommendations().isEmpty()) {
            throw new IllegalArgumentException("Response missing recommendations array");
        }
        
        for (RecommendationResponse.EntityRecommendation rec : response.getRecommendations()) {
            if (rec.getTableName() == null || rec.getTableName().trim().isEmpty()) {
                throw new IllegalArgumentException("Recommendation missing table name");
            }
            if (rec.getPartitionKey() == null) {
                throw new IllegalArgumentException("Recommendation missing partition key");
            }
        }
    }
}