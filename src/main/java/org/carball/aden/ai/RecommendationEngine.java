// RecommendationEngine.java
package org.carball.aden.ai;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.carball.aden.model.analysis.*;
import org.carball.aden.model.recommendation.*;
import org.carball.aden.model.schema.*;
import org.carball.aden.model.query.*;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class RecommendationEngine {

    private final OpenAiService openAiService;
    private static final String MODEL = "gpt-4";
    private static final double TEMPERATURE = 0.1;
    private static final int MAX_TOKENS = 1500;

    private static final String SYSTEM_PROMPT = """
        You are an expert AWS solutions architect specializing in .NET Framework
        to cloud migrations. Your expertise includes:
        
        - Legacy .NET Framework and Entity Framework patterns
        - AWS NoSQL services (DynamoDB, DocumentDB, Neptune)
        - Data modeling for cloud-native applications
        - Cost optimization strategies
        - Single table design patterns for DynamoDB
        
        Provide specific, actionable recommendations for migrating SQL Server
        + Entity Framework applications to AWS NoSQL services. Focus on:
        
        - Choosing the right AWS service for each use case
        - Designing efficient partition key strategies
        - Minimizing query costs and maximizing performance
        - Realistic cost comparisons with legacy infrastructure
        
        Always provide concrete examples and avoid generic advice.
        Format your response in structured sections for easy parsing.
        """;

    public RecommendationEngine(String apiKey) {
        // Only initialize OpenAI service if we're actually going to use it
        if (!"true".equals(System.getProperty("skip.ai"))) {
            this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(60));
        } else {
            this.openAiService = null;
        }
    }

    public List<NoSQLRecommendation> generateRecommendations(AnalysisResult analysis) {
        return generateRecommendations(analysis, null, null);
    }

    public List<NoSQLRecommendation> generateRecommendations(AnalysisResult analysis, 
                                                            DatabaseSchema schema, 
                                                            List<QueryPattern> queryPatterns) {
        return generateRecommendations(analysis, schema, queryPatterns, null);
    }
    
    public List<NoSQLRecommendation> generateRecommendations(AnalysisResult analysis, 
                                                            DatabaseSchema schema, 
                                                            List<QueryPattern> queryPatterns,
                                                            Map<String, Object> productionMetrics) {
        List<NoSQLRecommendation> recommendations = new ArrayList<>();

        // Check if we should skip AI
        if ("true".equals(System.getProperty("skip.ai"))) {
            log.info("Skipping AI recommendations (skip.ai=true), using fallback recommendations");

            for (DenormalizationCandidate candidate : analysis.getDenormalizationCandidates()) {
                NoSQLRecommendation fallback = createFallbackRecommendation(candidate);
                recommendations.add(fallback);
            }

            return recommendations;
        }

        log.info("Generating AI recommendations for {} candidates",
                analysis.getDenormalizationCandidates().size());

        for (DenormalizationCandidate candidate : analysis.getDenormalizationCandidates()) {
            try {
                NoSQLRecommendation recommendation = generateSingleRecommendation(candidate, analysis, schema, queryPatterns, productionMetrics);
                recommendations.add(recommendation);

                log.debug("Generated recommendation for {}: {} ({})",
                        candidate.getPrimaryEntity(),
                        recommendation.getTargetService(),
                        recommendation.getEstimatedCostSaving());

            } catch (Exception e) {
                log.error("Error generating recommendation for {}: {}",
                        candidate.getPrimaryEntity(), e.getMessage());

                // Create fallback recommendation
                NoSQLRecommendation fallback = createFallbackRecommendation(candidate);
                recommendations.add(fallback);
            }
        }

        return recommendations;
    }

    private NoSQLRecommendation generateSingleRecommendation(
            DenormalizationCandidate candidate, AnalysisResult analysis,
            DatabaseSchema schema, List<QueryPattern> queryPatterns,
            Map<String, Object> productionMetrics) {

        String prompt = buildRecommendationPrompt(candidate, analysis, schema, queryPatterns, productionMetrics);

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(MODEL)
                .messages(Arrays.asList(
                        new ChatMessage(ChatMessageRole.SYSTEM.value(), SYSTEM_PROMPT),
                        new ChatMessage(ChatMessageRole.USER.value(), prompt)
                ))
                .temperature(TEMPERATURE)
                .maxTokens(MAX_TOKENS)
                .build();

        log.trace("Sending prompt to OpenAI for entity: {}", candidate.getPrimaryEntity());
        log.trace("System prompt:\n{}", SYSTEM_PROMPT);
        log.trace("User prompt:\n{}", prompt);

        ChatCompletionResult result = openAiService.createChatCompletion(request);
        String response = result.getChoices().get(0).getMessage().getContent();

        log.trace("Received response: {} characters", response.length());
        log.trace("OpenAI response:\n{}", response);

        return parseRecommendationResponse(response, candidate);
    }

    private String buildRecommendationPrompt(DenormalizationCandidate candidate,
                                             AnalysisResult analysis,
                                             DatabaseSchema schema,
                                             List<QueryPattern> queryPatterns,
                                             Map<String, Object> productionMetrics) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("## Entity Analysis:\n\n");
        prompt.append("**Primary Entity:** ").append(candidate.getPrimaryEntity()).append("\n");
        prompt.append("**Related Entities:** ").append(String.join(", ", candidate.getRelatedEntities())).append("\n");
        prompt.append("**Migration Complexity:** ").append(candidate.getComplexity()).append("\n");
        prompt.append("**Denormalization Reason:** ").append(candidate.getReason()).append("\n");
        prompt.append("**Initial Target Suggestion:** ").append(candidate.getRecommendedTarget().getDisplayName()).append("\n\n");

        // Add usage profile details
        EntityUsageProfile profile = analysis.getUsageProfiles().get(candidate.getPrimaryEntity());
        if (profile != null) {
            prompt.append("## Usage Patterns:\n\n");
            prompt.append("**Eager Loading Frequency:** ").append(profile.getEagerLoadingCount()).append(" occurrences\n");
            prompt.append("**Read/Write Ratio:** ").append(String.format("%.1f", profile.getReadToWriteRatio())).append(":1\n");
            prompt.append("**Always Loaded With:** ").append(
                    profile.getAlwaysLoadedWithEntities().isEmpty() ? "None" :
                            String.join(", ", profile.getAlwaysLoadedWithEntities())
            ).append("\n");
            prompt.append("**Access Pattern:** ").append(
                    profile.hasSimpleKeyBasedAccess() ? "Simple key-based" : "Complex queries"
            ).append("\n\n");
        }

        // Add detailed schema information if available
        if (schema != null) {
            prompt.append("## Database Schema Context:\n\n");
            prompt.append(serializeTableSchema(candidate.getPrimaryEntity(), schema));
            prompt.append("\n");
            prompt.append(serializeRelationships(candidate.getPrimaryEntity(), schema));
            prompt.append("\n");
        }

        // Add query pattern information if available
        if (queryPatterns != null && !queryPatterns.isEmpty()) {
            prompt.append("## Query Pattern Analysis:\n\n");
            prompt.append(serializeQueryPatterns(candidate.getPrimaryEntity(), queryPatterns));
            prompt.append("\n");
            prompt.append(serializeEntityFrameworkContext(candidate.getPrimaryEntity(), queryPatterns));
            prompt.append("\n");
        }
        
        // Add production metrics from Query Store if available
        if (productionMetrics != null && !productionMetrics.isEmpty()) {
            prompt.append("## Production Usage Metrics (from SQL Server Query Store):\n\n");
            prompt.append(serializeProductionMetrics(candidate, productionMetrics));
            prompt.append("\n");
        }

        prompt.append("## Requirements:\n\n");
        prompt.append("Please provide a specific AWS NoSQL recommendation with the following structure:\n\n");
        prompt.append("1. **Target Service**: [DynamoDB/DocumentDB/Neptune] with justification\n");
        prompt.append("2. **Table/Collection Design**: Specific schema design\n");
        prompt.append("3. **Partition Key Strategy**: Attribute name, type, and example values\n");
        prompt.append("4. **Sort Key Strategy** (if applicable): Attribute name, type, and example values\n");
        prompt.append("5. **Global Secondary Indexes**: Name, keys, and purpose for each GSI\n");
        prompt.append("6. **Access Patterns**: How common queries will work\n");
        prompt.append("7. **Cost Estimation**: Percentage savings vs SQL Server Enterprise\n");
        prompt.append("8. **Migration Effort**: Time estimate and complexity\n\n");

        prompt.append("For DynamoDB, consider single table design if appropriate. ");
        prompt.append("Provide concrete examples for all key strategies.\n");

        return prompt.toString();
    }

    private NoSQLRecommendation parseRecommendationResponse(String response,
                                                            DenormalizationCandidate candidate) {
        NoSQLRecommendation recommendation = new NoSQLRecommendation();
        recommendation.setPrimaryEntity(candidate.getPrimaryEntity());

        // Parse target service
        NoSQLTarget target = parseTargetService(response);
        recommendation.setTargetService(target != null ? target : candidate.getRecommendedTarget());

        // Parse table name
        String tableName = parseSection(response, "Table/Collection Design", "Table Name");
        recommendation.setTableName(tableName != null ? tableName :
                candidate.getPrimaryEntity() + (target == NoSQLTarget.DYNAMODB ? "Table" : "Collection"));

        // Parse partition key
        recommendation.setPartitionKey(parseKeyStrategy(response, "Partition Key"));

        // Parse sort key
        recommendation.setSortKey(parseKeyStrategy(response, "Sort Key"));

        // Parse GSIs
        recommendation.setGlobalSecondaryIndexes(parseGSIs(response));

        // Parse cost estimation
        String costSaving = parseSection(response, "Cost Estimation", "savings");
        recommendation.setEstimatedCostSaving(costSaving != null ? costSaving : "40-60% reduction");

        // Parse migration effort
        String effort = parseSection(response, "Migration Effort", "weeks");
        recommendation.setMigrationEffort(effort != null ? effort : "2-4 weeks");

        // Store full schema design
        recommendation.setSchemaDesign(response);

        return recommendation;
    }

    private NoSQLTarget parseTargetService(String response) {
        Pattern pattern = Pattern.compile("\\*\\*Target Service\\*\\*:?\\s*\\[?([^]\\n]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            String service = matcher.group(1).toLowerCase();
            if (service.contains("dynamodb")) {
                return NoSQLTarget.DYNAMODB;
            } else if (service.contains("documentdb")) {
                return NoSQLTarget.DOCUMENTDB;
            } else if (service.contains("neptune")) {
                return NoSQLTarget.NEPTUNE;
            }
        }

        return null;
    }

    private String parseSection(String response, String sectionName, String keyword) {
        // Try to find section by name
        Pattern sectionPattern = Pattern.compile(
                "\\*\\*" + sectionName + "\\*\\*:?\\s*([^*]+)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = sectionPattern.matcher(response);

        if (matcher.find()) {
            String sectionContent = matcher.group(1);

            // Look for specific keyword if provided
            if (keyword != null) {
                Pattern keywordPattern = Pattern.compile(
                        keyword + "[:\\s]+([^\\n]+)",
                        Pattern.CASE_INSENSITIVE
                );
                Matcher keywordMatcher = keywordPattern.matcher(sectionContent);
                if (keywordMatcher.find()) {
                    return keywordMatcher.group(1).trim();
                }
            }

            // Return first line of section if no keyword
            String[] lines = sectionContent.trim().split("\n");
            if (lines.length > 0) {
                return lines[0].trim();
            }
        }

        return null;
    }

    private KeyStrategy parseKeyStrategy(String response, String keyType) {
        Pattern pattern = Pattern.compile(
                "\\*\\*" + keyType + "\\s*(?:Strategy)?\\*\\*:?\\s*([^*]+)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            String keyContent = matcher.group(1);

            // Extract attribute name
            Pattern attrPattern = Pattern.compile("(?:Attribute|Name):?\\s*`?([\\w#]+)`?", Pattern.CASE_INSENSITIVE);
            Matcher attrMatcher = attrPattern.matcher(keyContent);
            String attribute = attrMatcher.find() ? attrMatcher.group(1) : keyType.toLowerCase() + "Key";

            // Extract type
            Pattern typePattern = Pattern.compile("Type:?\\s*([SNB])", Pattern.CASE_INSENSITIVE);
            Matcher typeMatcher = typePattern.matcher(keyContent);
            String type = typeMatcher.find() ? typeMatcher.group(1) : "S";

            // Extract examples
            Pattern examplePattern = Pattern.compile("(?:Example|e\\.g\\.)[:\\s]*([^\\n]+)", Pattern.CASE_INSENSITIVE);
            Matcher exampleMatcher = examplePattern.matcher(keyContent);
            List<String> examples = new ArrayList<>();
            if (exampleMatcher.find()) {
                String exampleStr = exampleMatcher.group(1);
                examples.addAll(Arrays.asList(exampleStr.split("[,;]")));
            }

            return new KeyStrategy(attribute, type, keyContent.trim(), examples);
        }

        return null;
    }

    private List<GSIStrategy> parseGSIs(String response) {
        List<GSIStrategy> gsis = new ArrayList<>();

        Pattern gsiPattern = Pattern.compile(
                "(?:GSI|Index)\\s*(?:\\d+)?:?\\s*([^\\n]+)\\n\\s*-?\\s*Partition Key:?\\s*([^\\n]+)\\n\\s*-?\\s*Sort Key:?\\s*([^\\n]+)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = gsiPattern.matcher(response);

        while (matcher.find()) {
            String indexName = matcher.group(1).replaceAll("[*`]", "").trim();
            String partitionKey = matcher.group(2).replaceAll("[*`]", "").trim();
            String sortKey = matcher.group(3).replaceAll("[*`]", "").trim();

            GSIStrategy gsi = GSIStrategy.builder()
                    .indexName(indexName)
                    .partitionKey(partitionKey)
                    .sortKey(sortKey.equals("None") || sortKey.equals("N/A") ? null : sortKey)
                    .purpose("Enable efficient queries")
                    .build();

            gsis.add(gsi);
        }

        // If no GSIs found, try alternative pattern
        if (gsis.isEmpty()) {
            Pattern altPattern = Pattern.compile(
                    "Index Name:?\\s*([^\\n]+)",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher altMatcher = altPattern.matcher(response);
            while (altMatcher.find()) {
                String indexName = altMatcher.group(1).trim();
                GSIStrategy gsi = GSIStrategy.builder()
                        .indexName(indexName)
                        .partitionKey("tbd")
                        .sortKey("tbd")
                        .purpose("To be determined")
                        .build();
                gsis.add(gsi);
            }
        }

        return gsis;
    }

    private NoSQLRecommendation createFallbackRecommendation(DenormalizationCandidate candidate) {
        NoSQLRecommendation recommendation = new NoSQLRecommendation();
        recommendation.setPrimaryEntity(candidate.getPrimaryEntity());
        recommendation.setTargetService(candidate.getRecommendedTarget());
        recommendation.setTableName(candidate.getPrimaryEntity() + "Table");

        // Create basic partition key
        recommendation.setPartitionKey(new KeyStrategy(
                candidate.getPrimaryEntity().toLowerCase() + "Id",
                "S",
                "Primary key for " + candidate.getPrimaryEntity(),
                Arrays.asList("CUST123", "CUST456")
        ));

        // Create sort key if there are related entities
        if (!candidate.getRelatedEntities().isEmpty()) {
            recommendation.setSortKey(new KeyStrategy(
                    "entityType#id",
                    "S",
                    "Composite sort key for related entities",
                    Arrays.asList("PROFILE#metadata", "ORDER#12345")
            ));
        }

        // Basic GSI
        List<GSIStrategy> gsis = new ArrayList<>();
        gsis.add(GSIStrategy.builder()
                .indexName("StatusIndex")
                .partitionKey("status")
                .sortKey("createdAt")
                .purpose("Query by status")
                .build());
        recommendation.setGlobalSecondaryIndexes(gsis);

        recommendation.setEstimatedCostSaving("40-60% reduction vs SQL Server");
        recommendation.setMigrationEffort("2-4 weeks");
        recommendation.setSchemaDesign("Fallback recommendation - AI service unavailable");

        return recommendation;
    }

    /**
     * Serializes detailed table schema information for a specific entity
     */
    private String serializeTableSchema(String entityName, DatabaseSchema schema) {
        StringBuilder tableInfo = new StringBuilder();
        
        Table table = schema.findTable(entityName);
        if (table == null) {
            return "Table not found in schema";
        }
        
        tableInfo.append("**Table:** ").append(table.getName()).append("\n");
        
        // Primary key information
        if (table.getPrimaryKey() != null) {
            Column pk = table.getPrimaryKey();
            tableInfo.append("**Primary Key:** ").append(pk.getName())
                    .append(" (").append(pk.getDataType()).append(")\n");
        }
        
        // Column details
        tableInfo.append("**Columns:**\n");
        for (Column column : table.getColumns()) {
            tableInfo.append("  - ").append(column.getName())
                    .append(" (").append(column.getDataType()).append(")")
                    .append(column.isNullable() ? " NULL" : " NOT NULL");
            
            if (column.getDefaultValue() != null) {
                tableInfo.append(" DEFAULT ").append(column.getDefaultValue());
            }
            
            if (column.isForeignKey()) {
                tableInfo.append(" FK -> ").append(column.getReferencedTable())
                        .append(".").append(column.getReferencedColumn());
            }
            
            tableInfo.append("\n");
        }
        
        // Index information
        if (!table.getIndexes().isEmpty()) {
            tableInfo.append("**Indexes:**\n");
            for (Index index : table.getIndexes()) {
                tableInfo.append("  - ").append(index.getName())
                        .append(" (").append(String.join(", ", index.getColumns())).append(")")
                        .append(index.isUnique() ? " UNIQUE" : "")
                        .append("\n");
            }
        }
        
        return tableInfo.toString();
    }

    /**
     * Serializes relationship information for a specific entity
     */
    private String serializeRelationships(String entityName, DatabaseSchema schema) {
        StringBuilder relationshipInfo = new StringBuilder();
        
        relationshipInfo.append("**Relationships:**\n");
        
        List<Relationship> entityRelationships = schema.getRelationships().stream()
                .filter(r -> r.getFromTable().equalsIgnoreCase(entityName) || 
                           r.getToTable().equalsIgnoreCase(entityName))
                .toList();
        
        if (entityRelationships.isEmpty()) {
            relationshipInfo.append("  - No relationships found\n");
            return relationshipInfo.toString();
        }
        
        for (Relationship relationship : entityRelationships) {
            relationshipInfo.append("  - ");
            
            if (relationship.getFromTable().equalsIgnoreCase(entityName)) {
                // Outgoing relationship
                relationshipInfo.append(relationship.getFromTable())
                        .append(".").append(relationship.getFromColumn())
                        .append(" -> ").append(relationship.getToTable())
                        .append(".").append(relationship.getToColumn())
                        .append(" (").append(relationship.getType()).append(")\n");
            } else {
                // Incoming relationship
                relationshipInfo.append(relationship.getToTable())
                        .append(".").append(relationship.getToColumn())
                        .append(" <- ").append(relationship.getFromTable())
                        .append(".").append(relationship.getFromColumn())
                        .append(" (").append(relationship.getType()).append(")\n");
            }
        }
        
        return relationshipInfo.toString();
    }

    /**
     * Serializes query patterns for a specific entity
     */
    private String serializeQueryPatterns(String entityName, List<QueryPattern> patterns) {
        StringBuilder queryInfo = new StringBuilder();
        
        queryInfo.append("**Query Patterns:**\n");
        
        List<QueryPattern> entityPatterns = patterns.stream()
                .filter(p -> p.getTargetEntity().equalsIgnoreCase(entityName) ||
                           p.getTargetEntity().contains(entityName))
                .toList();
        
        if (entityPatterns.isEmpty()) {
            queryInfo.append("  - No query patterns found\n");
            return queryInfo.toString();
        }
        
        for (QueryPattern pattern : entityPatterns) {
            queryInfo.append("  - **").append(pattern.getQueryType()).append("**")
                    .append(" (frequency: ").append(pattern.getFrequency()).append(")\n");
            
            if (pattern.hasWhereClause()) {
                queryInfo.append("    WHERE columns: ")
                        .append(String.join(", ", pattern.getWhereClauseColumns()))
                        .append("\n");
                
                if (!pattern.getFilterConditions().isEmpty()) {
                    queryInfo.append("    Filter conditions:\n");
                    for (FilterCondition condition : pattern.getFilterConditions()) {
                        queryInfo.append("      - ").append(condition.getColumn())
                                .append(" ").append(condition.getOperator())
                                .append(" [").append(condition.getValueType()).append("]")
                                .append(" (freq: ").append(condition.getFrequency()).append(")\n");
                    }
                }
            }
            
            if (pattern.hasOrderBy()) {
                queryInfo.append("    ORDER BY: ")
                        .append(String.join(", ", pattern.getOrderByColumns()))
                        .append("\n");
            }
            
            if (pattern.hasJoins()) {
                queryInfo.append("    JOINS: ")
                        .append(String.join(", ", pattern.getJoinedEntities()))
                        .append("\n");
            }
            
            if (pattern.isHasAggregation()) {
                queryInfo.append("    Contains aggregation functions\n");
            }
            
            if (pattern.isHasPagination()) {
                queryInfo.append("    Uses pagination (Skip/Take)\n");
            }
            
            if (pattern.isHasComplexWhere()) {
                queryInfo.append("    Complex WHERE clause with multiple conditions\n");
            }
        }
        
        return queryInfo.toString();
    }

    /**
     * Serializes Entity Framework specific context information
     */
    private String serializeEntityFrameworkContext(String entityName, List<QueryPattern> patterns) {
        StringBuilder efInfo = new StringBuilder();
        
        efInfo.append("**Entity Framework Context:**\n");
        
        // Find eager loading patterns
        List<QueryPattern> eagerLoadingPatterns = patterns.stream()
                .filter(p -> p.getQueryType() == QueryType.EAGER_LOADING ||
                           p.getQueryType() == QueryType.COMPLEX_EAGER_LOADING)
                .filter(p -> p.getTargetEntity().contains(entityName))
                .toList();
        
        if (!eagerLoadingPatterns.isEmpty()) {
            efInfo.append("**Navigation Properties (Include patterns):**\n");
            for (QueryPattern pattern : eagerLoadingPatterns) {
                efInfo.append("  - ").append(pattern.getTargetEntity())
                        .append(" (frequency: ").append(pattern.getFrequency()).append(")\n");
            }
        }
        
        // Find parameter usage
        List<QueryPattern> parameterPatterns = patterns.stream()
                .filter(p -> p.getTargetEntity().equalsIgnoreCase(entityName) && 
                           !p.getParameterTypes().isEmpty())
                .toList();
        
        if (!parameterPatterns.isEmpty()) {
            efInfo.append("**Parameter Usage:**\n");
            Set<String> allParameters = new HashSet<>();
            for (QueryPattern pattern : parameterPatterns) {
                allParameters.addAll(pattern.getParameterTypes());
            }
            for (String param : allParameters) {
                efInfo.append("  - ").append(param).append("\n");
            }
        }
        
        // Query complexity indicators
        long complexQueryCount = patterns.stream()
                .filter(p -> p.getTargetEntity().equalsIgnoreCase(entityName))
                .filter(p -> p.isHasComplexWhere() || p.isHasAggregation() || 
                           p.getQueryType() == QueryType.COMPLEX_EAGER_LOADING)
                .count();
        
        if (complexQueryCount > 0) {
            efInfo.append("**Complexity Indicators:**\n");
            efInfo.append("  - Complex queries: ").append(complexQueryCount).append("\n");
        }
        
        return efInfo.toString();
    }
    
    /**
     * Serializes production metrics from Query Store analysis
     */
    private String serializeProductionMetrics(DenormalizationCandidate candidate, 
                                            Map<String, Object> productionMetrics) {
        StringBuilder metricsInfo = new StringBuilder();
        
        // Extract qualified metrics
        @SuppressWarnings("unchecked")
        Map<String, Object> qualifiedMetrics = (Map<String, Object>) productionMetrics.get("qualifiedMetrics");
        if (qualifiedMetrics == null) {
            return "No production metrics available\n";
        }
        
        // Total executions and operations breakdown
        Object totalExecutions = qualifiedMetrics.get("totalExecutions");
        if (totalExecutions != null) {
            metricsInfo.append("**Total Query Executions:** ")
                    .append(String.format("%,d", ((Number) totalExecutions).longValue()))
                    .append("\n");
        }
        
        // Operation breakdown
        @SuppressWarnings("unchecked")
        Map<String, Object> operations = (Map<String, Object>) qualifiedMetrics.get("operationBreakdown");
        if (operations != null && !operations.isEmpty()) {
            metricsInfo.append("**Operation Breakdown:**\n");
            operations.forEach((op, count) -> {
                metricsInfo.append("  - ").append(op).append(": ")
                        .append(String.format("%,d", ((Number) count).longValue()))
                        .append(" executions\n");
            });
        }
        
        // Read/Write ratio
        Object readWriteRatio = qualifiedMetrics.get("readWriteRatio");
        if (readWriteRatio != null) {
            metricsInfo.append("**Read/Write Ratio:** ")
                    .append(String.format("%.1f:1", ((Number) readWriteRatio).doubleValue()));
            
            Boolean isReadHeavy = (Boolean) qualifiedMetrics.get("isReadHeavy");
            if (Boolean.TRUE.equals(isReadHeavy)) {
                metricsInfo.append(" (Read-heavy workload)");
            }
            metricsInfo.append("\n");
        }
        
        // Table access patterns
        @SuppressWarnings("unchecked")
        Map<String, Object> tablePatterns = (Map<String, Object>) qualifiedMetrics.get("tableAccessPatterns");
        if (tablePatterns != null) {
            Boolean hasCoAccess = (Boolean) tablePatterns.get("hasStrongCoAccessPatterns");
            if (Boolean.TRUE.equals(hasCoAccess)) {
                metricsInfo.append("\n**Table Co-Access Patterns:**\n");
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> combinations = 
                    (List<Map<String, Object>>) tablePatterns.get("frequentTableCombinations");
                
                if (combinations != null) {
                    for (Map<String, Object> combo : combinations) {
                        @SuppressWarnings("unchecked")
                        List<String> tables = (List<String>) combo.get("tables");
                        
                        // Check if this combination includes our candidate entity
                        boolean relevantPattern = tables.stream()
                            .anyMatch(table -> table.equalsIgnoreCase(candidate.getPrimaryEntity()) ||
                                             candidate.getRelatedEntities().stream()
                                                 .anyMatch(related -> table.equalsIgnoreCase(related)));
                        
                        if (relevantPattern) {
                            Object executions = combo.get("totalExecutions");
                            Object percentage = combo.get("executionPercentage");
                            
                            metricsInfo.append("  - **").append(String.join(" + ", tables)).append("**")
                                    .append(" accessed together ")
                                    .append(String.format("%,d", ((Number) executions).longValue()))
                                    .append(" times");
                            
                            if (percentage != null) {
                                metricsInfo.append(" (").append(String.format("%.1f%%", 
                                        ((Number) percentage).doubleValue()))
                                        .append(" of all queries)");
                            }
                            metricsInfo.append("\n");
                        }
                    }
                }
            }
        }
        
        // Performance characteristics
        @SuppressWarnings("unchecked")
        Map<String, Object> performance = (Map<String, Object>) qualifiedMetrics.get("performanceCharacteristics");
        if (performance != null) {
            metricsInfo.append("\n**Performance Characteristics:**\n");
            
            Object avgDuration = performance.get("avgQueryDurationMs");
            if (avgDuration != null) {
                metricsInfo.append("  - Average Query Duration: ")
                        .append(String.format("%.2f ms", ((Number) avgDuration).doubleValue()))
                        .append("\n");
            }
            
            Object avgReads = performance.get("avgLogicalReads");
            if (avgReads != null) {
                metricsInfo.append("  - Average Logical Reads: ")
                        .append(String.format("%.0f", ((Number) avgReads).doubleValue()))
                        .append("\n");
            }
            
            Boolean hasPerformanceIssues = (Boolean) performance.get("hasPerformanceIssues");
            if (Boolean.TRUE.equals(hasPerformanceIssues)) {
                metricsInfo.append("  - **WARNING:** Slow queries detected (>100ms)\n");
            }
        }
        
        // Access pattern distribution
        @SuppressWarnings("unchecked")
        Map<String, Object> accessPatterns = (Map<String, Object>) qualifiedMetrics.get("accessPatternDistribution");
        if (accessPatterns != null && !accessPatterns.isEmpty()) {
            metricsInfo.append("\n**Access Pattern Distribution:**\n");
            accessPatterns.forEach((pattern, count) -> {
                metricsInfo.append("  - ").append(pattern).append(": ")
                        .append(count).append(" queries\n");
            });
        }
        
        return metricsInfo.toString();
    }
}