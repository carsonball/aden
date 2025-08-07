package org.carball.aden.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.*;
import lombok.extern.slf4j.Slf4j;
import org.carball.aden.model.analysis.*;
import org.carball.aden.model.recommendation.*;
import org.carball.aden.model.schema.*;
import org.carball.aden.model.query.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JSON-based recommendation engine that uses structured outputs for reliable parsing.
 */
@Slf4j
public class JsonRecommendationEngine {

    private final OpenAIClient openAiClient;
    private final ObjectMapper objectMapper;
    private static final String MODEL = "gpt-4-turbo-preview";
    private static final double TEMPERATURE = 0.1;
    private static final int MAX_TOKENS = 4000;

    private static final String SYSTEM_PROMPT = """
        You are an expert AWS solutions architect specializing in .NET Framework
        to cloud migrations. Your expertise includes:
        
        - Legacy .NET Framework and Entity Framework patterns
        - AWS NoSQL services (DynamoDB, DocumentDB, Neptune)
        - Data modeling for cloud-native applications
        - Single table design patterns for DynamoDB
        
        You must respond with valid JSON that matches the specified schema exactly.
        Do not include any text outside of the JSON response.
        """;

    public JsonRecommendationEngine(String apiKey) {
        if (!"true".equals(System.getProperty("skip.ai"))) {
            this.openAiClient = OpenAIOkHttpClient.builder()
                    .apiKey(apiKey)
                    .build();
        } else {
            this.openAiClient = null;
        }
        
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public List<NoSQLRecommendation> generateRecommendations(AnalysisResult analysis) {
        return generateRecommendations(analysis, null, null, null);
    }

    public List<NoSQLRecommendation> generateRecommendations(AnalysisResult analysis, 
                                                            DatabaseSchema schema, 
                                                            List<QueryPattern> queryPatterns,
                                                            QueryStoreAnalysis productionMetrics) {
        List<NoSQLRecommendation> recommendations = new ArrayList<>();

        if ("true".equals(System.getProperty("skip.ai"))) {
            log.info("Skipping AI recommendations (skip.ai=true), using fallback recommendations");
            for (DenormalizationCandidate candidate : analysis.getDenormalizationCandidates()) {
                recommendations.add(createFallbackRecommendation(candidate));
            }
            return recommendations;
        }

        log.info("Generating AI recommendations for {} candidates",
                analysis.getDenormalizationCandidates().size());

        try {
            String prompt = buildJsonPrompt(analysis, schema, queryPatterns, productionMetrics);
            String jsonResponse = callOpenAIWithJsonMode(prompt);
            
            // Validate JSON structure
            RecommendationResponse response = parseAndValidateResponse(jsonResponse);
            
            // Validate we have recommendations for all candidates
            validateCompleteness(response, analysis.getDenormalizationCandidates());
            
            recommendations = convertToNoSQLRecommendations(response);
            
            log.info("Successfully parsed {} recommendations from JSON response", recommendations.size());

        } catch (Exception e) {
            log.error("Error generating JSON recommendations: {}", e.getMessage(), e);
            
            // Fallback to individual recommendations
            for (DenormalizationCandidate candidate : analysis.getDenormalizationCandidates()) {
                recommendations.add(createFallbackRecommendation(candidate));
            }
        }

        return recommendations;
    }

    private String callOpenAIWithJsonMode(String prompt) {
        // Add explicit JSON instruction to prompt
        String jsonInstructionPrompt = prompt + "\n\nIMPORTANT: Respond ONLY with valid JSON. Do not include any text before or after the JSON object.";
        
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(MODEL)
                .addSystemMessage(SYSTEM_PROMPT)
                .addUserMessage(jsonInstructionPrompt)
                .temperature(TEMPERATURE)
                .maxCompletionTokens(MAX_TOKENS)
                .responseFormat(ResponseFormatJsonObject.builder().build())
                .build();

        log.trace("Sending JSON mode request to OpenAI");
        log.debug("Request model: {}", MODEL);
        log.debug("System prompt: {}", SYSTEM_PROMPT);
        log.debug("User prompt length: {} characters", jsonInstructionPrompt.length());
        
        // Log full prompt in trace mode for debugging
        log.trace("Full user prompt:\n{}", jsonInstructionPrompt);
        
        ChatCompletion completion = openAiClient.chat().completions().create(params);
        
        // Extract content from the completion
        String response = "";
        try {
            // The official SDK might have a different structure
            var choice = completion.choices().get(0);
            
            // Based on the error, it seems the message might have a different structure
            // Let's try to access it more carefully
            var message = choice.message();
            
            // The content might be a complex object, not just a string
            // Try different ways to get the content
            try {
                response = message.content().orElse("");
            } catch (Exception e) {
                // If content() doesn't work, try other approaches
                log.debug("Direct content access failed: {}", e.getMessage());

                // The error shows content is there but in a different format
                // Let's extract it from the string representation
                String messageStr = message.toString();
                int contentStart = messageStr.indexOf("content=");
                if (contentStart != -1) {
                    contentStart += 8; // length of "content="
                    int contentEnd = messageStr.indexOf(", refusal=", contentStart);
                    if (contentEnd == -1) {
                        contentEnd = messageStr.length() - 1;
                    }
                    response = messageStr.substring(contentStart, contentEnd);
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract response content: {}", e.getMessage());
            // Last resort - extract from error message which shows the content
            String errorMsg = e.getMessage();
            int jsonStart = errorMsg.indexOf("content={");
            if (jsonStart != -1) {
                jsonStart += 8; // length of "content="
                int jsonEnd = errorMsg.lastIndexOf("}, refusal=");
                if (jsonEnd != -1) {
                    response = errorMsg.substring(jsonStart, jsonEnd + 1);
                    log.debug("Extracted JSON from error message");
                }
            } else {
                throw new RuntimeException("Unable to extract response from OpenAI", e);
            }
        }
        
        log.trace("Received JSON response: {} characters", response.length());
        
        // Clean response - remove any markdown code blocks if present
        response = response.trim();
        if (response.startsWith("```json")) {
            response = response.substring(7);
        }
        if (response.startsWith("```")) {
            response = response.substring(3);
        }
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }
        response = response.trim();
        
        return response;
    }
    
    private RecommendationResponse parseAndValidateResponse(String jsonResponse) throws Exception {
        RecommendationResponse response = objectMapper.readValue(jsonResponse, RecommendationResponse.class);
        
        // Validate required fields
        if (response.getRecommendations() == null || response.getRecommendations().isEmpty()) {
            throw new IllegalArgumentException("Response missing recommendations array");
        }
        
        for (RecommendationResponse.EntityRecommendation rec : response.getRecommendations()) {
            if (rec.getEntityName() == null || rec.getEntityName().trim().isEmpty()) {
                throw new IllegalArgumentException("Recommendation missing entity name");
            }
            if (rec.getTargetService() == null) {
                throw new IllegalArgumentException("Recommendation for " + rec.getEntityName() + " missing target service");
            }
            if (rec.getTableName() == null || rec.getTableName().trim().isEmpty()) {
                throw new IllegalArgumentException("Recommendation for " + rec.getEntityName() + " missing table name");
            }
            if (rec.getPartitionKey() == null) {
                throw new IllegalArgumentException("Recommendation for " + rec.getEntityName() + " missing partition key");
            }
        }
        
        return response;
    }
    
    private void validateCompleteness(RecommendationResponse response, List<DenormalizationCandidate> candidates) {
        Set<String> responseEntities = response.getRecommendations().stream()
                .map(RecommendationResponse.EntityRecommendation::getEntityName)
                .collect(Collectors.toSet());
        
        Set<String> missingEntities = candidates.stream()
                .map(DenormalizationCandidate::getPrimaryEntity)
                .filter(entity -> !responseEntities.contains(entity))
                .collect(Collectors.toSet());
        
        if (!missingEntities.isEmpty()) {
            log.warn("Response missing recommendations for entities: {}", missingEntities);
        }
    }

    private String buildJsonPrompt(AnalysisResult analysis,
                                  DatabaseSchema schema,
                                  List<QueryPattern> queryPatterns,
                                  QueryStoreAnalysis productionMetrics) {
        StringBuilder prompt = new StringBuilder();
        
        // Context section
        prompt.append("Analyze the following entities and provide AWS NoSQL migration recommendations.\n\n");
        
        prompt.append("## Database Overview:\n");
        prompt.append("Total Entities: ").append(analysis.getDenormalizationCandidates().size()).append("\n");
        if (schema != null) {
            prompt.append("Total Tables: ").append(schema.getTables().size()).append("\n");
            prompt.append("Total Relationships: ").append(schema.getRelationships().size()).append("\n");
        }
        prompt.append("\n");
        
        // Entity details
        prompt.append("## Entities to Analyze:\n");
        for (DenormalizationCandidate candidate : analysis.getDenormalizationCandidates()) {
            prompt.append("\n### Entity: ").append(candidate.getPrimaryEntity()).append("\n");
            prompt.append("- Related Entities: ").append(
                    candidate.getRelatedEntities().isEmpty() ? "None" : 
                    String.join(", ", candidate.getRelatedEntities())
            ).append("\n");
            prompt.append("- Migration Complexity: ").append(candidate.getComplexity()).append("\n");
            prompt.append("- Score: ").append(candidate.getScore()).append("\n");
            prompt.append("- Selection Reason: ").append(candidate.getReason()).append("\n");
            
            EntityUsageProfile profile = analysis.getUsageProfiles().get(candidate.getPrimaryEntity());
            if (profile != null) {
                prompt.append("- Eager Loading Count: ").append(profile.getEagerLoadingCount()).append("\n");
                prompt.append("- Read/Write Ratio: ").append(String.format("%.1f:1", profile.getReadToWriteRatio())).append("\n");
                prompt.append("- Access Pattern: ").append(
                        profile.hasSimpleKeyBasedAccess() ? "Simple key-based" : "Complex queries"
                ).append("\n");
                
                // Add production metrics if available
                if (profile.getProductionExecutionCount() > 0) {
                    prompt.append("- Production Execution Count: ").append(profile.getProductionExecutionCount()).append("\n");
                    prompt.append("- Production Read/Write Ratio: ").append(
                            String.format("%.1f:1", profile.getProductionReadWriteRatio())
                    ).append("\n");
                }
                
                // Add co-accessed entities (85% threshold)
                if (!profile.getCoAccessedEntities().isEmpty()) {
                    List<Map.Entry<String, Integer>> sortedCoAccess = profile.getCoAccessedEntities().entrySet().stream()
                            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                            .toList();
                    
                    int totalCount = sortedCoAccess.stream().mapToInt(Map.Entry::getValue).sum();
                    int threshold = (int)(totalCount * 0.85);
                    int cumulativeCount = 0;
                    
                    prompt.append("- Frequently Co-Accessed With: ");
                    for (Map.Entry<String, Integer> entry : sortedCoAccess) {
                        if (cumulativeCount >= threshold) break;
                        prompt.append(entry.getKey()).append(" (").append(entry.getValue()).append("x), ");
                        cumulativeCount += entry.getValue();
                    }
                    if (prompt.toString().endsWith(", ")) {
                        prompt.setLength(prompt.length() - 2); // Remove trailing comma
                    }
                    prompt.append("\n");
                }
                
                // Add relationship types
                if (!profile.getRelatedEntities().isEmpty()) {
                    prompt.append("- Relationship Types: ");
                    profile.getRelatedEntities().forEach((entity, type) -> 
                            prompt.append(entity).append(" (").append(type).append("), "));
                    prompt.setLength(prompt.length() - 2); // Remove trailing comma
                    prompt.append("\n");
                }
            }
            
            // Add query patterns if available
            if (queryPatterns != null && !queryPatterns.isEmpty()) {
                prompt.append(serializeQueryPatterns(candidate.getPrimaryEntity(), queryPatterns));
            }
        }
        
        // Production metrics summary if available
        if (productionMetrics != null) {
            appendProductionMetricsSummary(prompt, productionMetrics);
        }
        
        // JSON schema and requirements
        prompt.append("\n## Response Requirements:\n");
        prompt.append("Provide a JSON response with this exact structure:\n");
        prompt.append(getJsonSchemaExample());
        prompt.append("\n\nEnsure all entities are included in the recommendations array.");
        prompt.append("\nConsider single-table design where entities are frequently accessed together.");
        prompt.append("\nProvide specific, actionable recommendations for each entity.");
        prompt.append("\n\nIMPORTANT: For each entity, provide a detailed designRationale that explains:");
        prompt.append("\n- How the usage patterns (eager loading, co-access, read/write ratios) influenced your design");
        prompt.append("\n- Why you chose the specific partition and sort keys based on the access patterns");
        prompt.append("\n- How relationships and co-access data shaped the denormalization strategy");
        prompt.append("\n- What performance optimizations were made based on production metrics");
        prompt.append("\n- Trade-offs between different design approaches");
        
        return prompt.toString();
    }

    private String getJsonSchemaExample() {
        return """
        {
          "recommendations": [
            {
              "entityName": "Customer",
              "targetService": "DynamoDB",
              "justification": "High-volume key-based access patterns",
              "tableName": "CustomerData",
              "partitionKey": {
                "attributeName": "customerId",
                "attributeType": "S",
                "description": "Unique customer identifier",
                "exampleValues": ["CUST123", "CUST456"]
              },
              "sortKey": {
                "attributeName": "entityType#timestamp",
                "attributeType": "S",
                "description": "Composite key for entity type and timestamp",
                "exampleValues": ["PROFILE#2024-01-15", "ORDER#2024-01-16"]
              },
              "globalSecondaryIndexes": [
                {
                  "indexName": "EmailIndex",
                  "partitionKey": "email",
                  "sortKey": "customerId",
                  "purpose": "Query customers by email",
                  "projectionType": "ALL"
                }
              ],
              "accessPatterns": [
                {
                  "name": "Get customer by ID",
                  "description": "Retrieve customer profile",
                  "keyCondition": "customerId = :id AND entityType = 'PROFILE'",
                  "indexUsed": null
                }
              ],
              "singleTableDesign": {
                "isRecommended": true,
                "sharedTableName": "ApplicationData",
                "entityDiscriminator": "entityType",
                "explanation": "Customer and Order entities are always accessed together"
              },
              "designRationale": {
                "denormalizationStrategy": "Single-table design combining Customer with frequently co-accessed Order and OrderItem entities based on 85% of co-access patterns",
                "keyDesignJustification": "CustomerId as partition key enables efficient customer lookups; composite sort key with entityType allows storing multiple entity types while maintaining query efficiency",
                "relationshipHandling": "1:N Customer-Order relationship preserved through sort key design; Order items embedded as nested documents to reduce joins",
                "performanceOptimizations": "GSI on email optimizes customer lookup pattern; sparse index on orderStatus reduces scan costs for order queries",
                "accessPatternAnalysis": "Analysis shows 95% of queries retrieve customer with recent orders, supporting single-table design. Read-heavy workload (10:1) favors denormalization",
                "tradeoffsConsidered": [
                  "Single table reduces join complexity but increases item size",
                  "Denormalization improves read performance but requires careful update orchestration",
                  "GSI costs offset by elimination of join operations"
                ]
              }
            }
          ],
          "overallStrategy": {
            "primaryApproach": "Single-table design for related entities",
            "keyConsiderations": ["High read volume", "Strong entity relationships"]
          }
        }
        """;
    }

    private void appendProductionMetricsSummary(StringBuilder prompt, QueryStoreAnalysis productionMetrics) {
        prompt.append("\n## Production Metrics Summary:\n");
        
        QualifiedMetrics qualifiedMetrics = productionMetrics.getQualifiedMetrics();
        if (qualifiedMetrics != null) {
            long totalExecutions = qualifiedMetrics.getTotalExecutions();
            prompt.append("- Total Query Executions: ")
                  .append(String.format("%,d", totalExecutions)).append("\n");
            
            double readWriteRatio = qualifiedMetrics.getReadWriteRatio();
            prompt.append("- Overall Read/Write Ratio: ")
                  .append(String.format("%.1f:1", readWriteRatio)).append("\n");
            
            TableAccessPatterns tablePatterns = qualifiedMetrics.getTableAccessPatterns();
            if (tablePatterns != null) {
                boolean hasCoAccess = tablePatterns.isHasStrongCoAccessPatterns();
                if (hasCoAccess) {
                    prompt.append("- Strong table co-access patterns detected\n");
                }
            }
        }
    }

    private String serializeQueryPatterns(String entityName, List<QueryPattern> patterns) {
        StringBuilder queryInfo = new StringBuilder();
        
        queryInfo.append("- Query Patterns:\n");
        
        List<QueryPattern> entityPatterns = patterns.stream()
                .filter(p -> p.getTargetEntity().equalsIgnoreCase(entityName) ||
                           p.getTargetEntity().contains(entityName))
                .toList();
        
        if (entityPatterns.isEmpty()) {
            queryInfo.append("  - No specific query patterns found\n");
            return queryInfo.toString();
        }
        
        // Group patterns by type to avoid duplicates and provide better context
        Map<String, Integer> patternCounts = new LinkedHashMap<>();
        for (QueryPattern pattern : entityPatterns) {
            String patternDesc = getPatternDescription(pattern.getQueryType().toString());
            patternCounts.merge(patternDesc, pattern.getFrequency(), Integer::sum);
        }
        
        // Output aggregated patterns with descriptions
        for (Map.Entry<String, Integer> entry : patternCounts.entrySet()) {
            queryInfo.append("  - ").append(entry.getKey())
                    .append(" (frequency: ").append(entry.getValue()).append(")\n");
        }
        
        // Add detailed information for patterns that have specific details
        boolean hasDetails = false;
        for (QueryPattern pattern : entityPatterns) {
            if (pattern.hasWhereClause() || pattern.hasOrderBy() || pattern.hasJoins()) {
                if (!hasDetails) {
                    queryInfo.append("  Pattern Details:\n");
                    hasDetails = true;
                }
                
                if (pattern.hasWhereClause()) {
                    queryInfo.append("    - WHERE columns: ")
                            .append(String.join(", ", pattern.getWhereClauseColumns()))
                            .append("\n");
                    
                    if (!pattern.getFilterConditions().isEmpty()) {
                        for (FilterCondition condition : pattern.getFilterConditions()) {
                            queryInfo.append("      - ").append(condition.getColumn())
                                    .append(" ").append(condition.getOperator())
                                    .append(" [").append(condition.getValueType()).append("]\n");
                        }
                    }
                }
                
                if (pattern.hasOrderBy()) {
                    queryInfo.append("    - ORDER BY: ")
                            .append(String.join(", ", pattern.getOrderByColumns()))
                            .append("\n");
                }
                
                if (pattern.hasJoins()) {
                    queryInfo.append("    - JOINS with: ")
                            .append(String.join(", ", pattern.getJoinedEntities()))
                            .append("\n");
                }
            }
        }
        
        return queryInfo.toString();
    }
    
    private String getPatternDescription(String patternType) {
        return switch (patternType) {
            case "SINGLE_ENTITY" -> "Single entity lookups (by ID or unique key)";
            case "COLLECTION" -> "Collection queries (multiple entities)";
            case "EAGER_LOADING" -> "Eager loading with related entities";
            case "COMPLEX_EAGER_LOADING" -> "Complex eager loading (multiple nested relationships)";
            case "WHERE_CLAUSE" -> "Filtered queries with WHERE conditions";
            case "ORDER_BY" -> "Sorted queries with ORDER BY";
            case "AGGREGATION" -> "Aggregation queries (COUNT, SUM, AVG, etc.)";
            case "PAGINATION" -> "Paginated queries (Skip/Take)";
            case "JOIN" -> "Queries with explicit JOINs";
            case "COMPLEX_WHERE" -> "Complex filtered queries (multiple conditions)";
            default -> patternType + " queries";
        };
    }

    private List<NoSQLRecommendation> convertToNoSQLRecommendations(
            RecommendationResponse response) {
        
        List<NoSQLRecommendation> recommendations = new ArrayList<>();
        
        for (RecommendationResponse.EntityRecommendation entityRec : response.getRecommendations()) {
            NoSQLRecommendation recommendation = new NoSQLRecommendation();
            recommendation.setPrimaryEntity(entityRec.getEntityName());
            
            // Convert target service
            recommendation.setTargetService(parseTargetService(entityRec.getTargetService()));
            recommendation.setTableName(entityRec.getTableName());
            
            // Convert keys
            if (entityRec.getPartitionKey() != null) {
                recommendation.setPartitionKey(convertKeyDefinition(entityRec.getPartitionKey()));
            }
            if (entityRec.getSortKey() != null) {
                recommendation.setSortKey(convertKeyDefinition(entityRec.getSortKey()));
            }
            
            // Convert GSIs
            if (entityRec.getGlobalSecondaryIndexes() != null) {
                recommendation.setGlobalSecondaryIndexes(
                    entityRec.getGlobalSecondaryIndexes().stream()
                        .map(this::convertGSI)
                        .collect(Collectors.toList())
                );
            }
            
            // Schema design - build from structured data
            recommendation.setSchemaDesign(buildSchemaDesignText(entityRec));
            
            // Design rationale
            if (entityRec.getDesignRationale() != null) {
                recommendation.setDesignRationale(convertDesignRationale(entityRec.getDesignRationale()));
            }
            
            recommendations.add(recommendation);
        }
        
        return recommendations;
    }

    private NoSQLTarget parseTargetService(String service) {
        if (service == null) return NoSQLTarget.DYNAMODB;

        return switch (service.toUpperCase()) {
            case "DOCUMENTDB" -> NoSQLTarget.DOCUMENTDB;
            case "NEPTUNE" -> NoSQLTarget.NEPTUNE;
            default -> NoSQLTarget.DYNAMODB;
        };
    }

    private KeyStrategy convertKeyDefinition(RecommendationResponse.KeyDefinition keyDef) {
        return new KeyStrategy(
            keyDef.getAttributeName(),
            keyDef.getAttributeType(),
            keyDef.getDescription(),
            keyDef.getExampleValues() != null ? keyDef.getExampleValues() : new ArrayList<>()
        );
    }

    private GSIStrategy convertGSI(RecommendationResponse.GSIDefinition gsiDef) {
        return GSIStrategy.builder()
                .indexName(gsiDef.getIndexName())
                .partitionKey(gsiDef.getPartitionKey())
                .sortKey(gsiDef.getSortKey())
                .purpose(gsiDef.getPurpose())
                .build();
    }

    private DesignRationale convertDesignRationale(RecommendationResponse.DesignRationale responseRationale) {
        return DesignRationale.builder()
                .denormalizationStrategy(responseRationale.getDenormalizationStrategy())
                .keyDesignJustification(responseRationale.getKeyDesignJustification())
                .relationshipHandling(responseRationale.getRelationshipHandling())
                .performanceOptimizations(responseRationale.getPerformanceOptimizations())
                .accessPatternAnalysis(responseRationale.getAccessPatternAnalysis())
                .tradeoffsConsidered(responseRationale.getTradeoffsConsidered() != null ? 
                        new ArrayList<>(responseRationale.getTradeoffsConsidered()) : new ArrayList<>())
                .build();
    }

    private String buildSchemaDesignText(RecommendationResponse.EntityRecommendation rec) {
        StringBuilder design = new StringBuilder();
        
        design.append("Target Service: ").append(rec.getTargetService()).append("\n");
        design.append("Justification: ").append(rec.getJustification()).append("\n\n");
        
        design.append("Table Design:\n");
        design.append("- Table Name: ").append(rec.getTableName()).append("\n");
        
        if (rec.getPartitionKey() != null) {
            design.append("- Partition Key: ").append(rec.getPartitionKey().getAttributeName())
                  .append(" (").append(rec.getPartitionKey().getAttributeType()).append(")\n");
        }
        
        if (rec.getSortKey() != null) {
            design.append("- Sort Key: ").append(rec.getSortKey().getAttributeName())
                  .append(" (").append(rec.getSortKey().getAttributeType()).append(")\n");
        }
        
        if (rec.getSingleTableDesign() != null && rec.getSingleTableDesign().getIsRecommended()) {
            design.append("\nSingle Table Design:\n");
            design.append("- Shared Table: ").append(rec.getSingleTableDesign().getSharedTableName()).append("\n");
            design.append("- Entity Discriminator: ").append(rec.getSingleTableDesign().getEntityDiscriminator()).append("\n");
            design.append("- Rationale: ").append(rec.getSingleTableDesign().getExplanation()).append("\n");
        }
        
        if (rec.getAccessPatterns() != null && !rec.getAccessPatterns().isEmpty()) {
            design.append("\nAccess Patterns:\n");
            for (RecommendationResponse.AccessPattern pattern : rec.getAccessPatterns()) {
                design.append("- ").append(pattern.getName()).append(": ")
                      .append(pattern.getDescription()).append("\n");
            }
        }
        
        return design.toString();
    }

    private NoSQLRecommendation createFallbackRecommendation(DenormalizationCandidate candidate) {
        NoSQLRecommendation recommendation = new NoSQLRecommendation();
        recommendation.setPrimaryEntity(candidate.getPrimaryEntity());
        recommendation.setTargetService(candidate.getRecommendedTarget());
        recommendation.setTableName(candidate.getPrimaryEntity() + "Table");

        recommendation.setPartitionKey(new KeyStrategy(
                candidate.getPrimaryEntity().toLowerCase() + "Id",
                "S",
                "Primary key for " + candidate.getPrimaryEntity(),
                Arrays.asList("ID123", "ID456")
        ));

        if (!candidate.getRelatedEntities().isEmpty()) {
            recommendation.setSortKey(new KeyStrategy(
                    "entityType#timestamp",
                    "S",
                    "Composite sort key for related entities",
                    Arrays.asList("PROFILE#2024-01-15", "ORDER#2024-01-16")
            ));
        }

        List<GSIStrategy> gsis = new ArrayList<>();
        gsis.add(GSIStrategy.builder()
                .indexName("StatusIndex")
                .partitionKey("status")
                .sortKey("createdAt")
                .purpose("Query by status")
                .build());
        recommendation.setGlobalSecondaryIndexes(gsis);

        recommendation.setSchemaDesign("Fallback recommendation - AI service unavailable");

        return recommendation;
    }
}