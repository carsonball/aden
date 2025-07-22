package org.carball.aden.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.carball.aden.config.MigrationThresholds;
import org.carball.aden.model.analysis.*;
import org.carball.aden.model.entity.EntityModel;
import org.carball.aden.model.entity.NavigationProperty;
import org.carball.aden.model.entity.NavigationType;
import org.carball.aden.model.query.QueryPattern;
import org.carball.aden.model.query.QueryType;
import org.carball.aden.model.schema.DatabaseSchema;
import org.carball.aden.model.schema.Relationship;
import org.carball.aden.model.schema.RelationshipType;
import org.carball.aden.model.schema.Table;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DotNetPatternAnalyzer {

    private final MigrationThresholds thresholds;
    private Map<String, EntityUsageProfile> profiles; // Instance variable for profile access

    public DotNetPatternAnalyzer(MigrationThresholds thresholds) {
        this.thresholds = thresholds;
    }

    public AnalysisResult analyzePatterns(List<EntityModel> entities,
                                          List<QueryPattern> queryPatterns,
                                          DatabaseSchema schema,
                                          Map<String, String> dbSetMapping) {
        return analyzePatterns(entities, queryPatterns, schema, dbSetMapping, null);
    }
    
    public AnalysisResult analyzePatterns(List<EntityModel> entities,
                                          List<QueryPattern> queryPatterns,
                                          DatabaseSchema schema,
                                          Map<String, String> dbSetMapping,
                                          Map<String, Object> productionMetrics) {

        log.info("Analyzing patterns for {} entities and {} query patterns using thresholds: {}",
                entities.size(), queryPatterns.size(), thresholds.getConfigurationSummary());
                
        // Provide configuration suggestions
        int maxFrequency = queryPatterns.stream()
                .mapToInt(QueryPattern::getFrequency)
                .max()
                .orElse(0);
        thresholds.suggestAdjustments(entities.size(), queryPatterns.size(), maxFrequency);

        AnalysisResult result = new AnalysisResult();

        // Build usage profiles
        Map<String, EntityUsageProfile> usageProfiles = buildUsageProfiles(entities, queryPatterns, dbSetMapping, productionMetrics);

        // Analyze relationships and access patterns
        analyzeRelationshipPatterns(usageProfiles, schema, productionMetrics);

        // Identify denormalization candidates
        List<DenormalizationCandidate> candidates = identifyDenormalizationCandidates(
                usageProfiles, schema);

        // Perform complexity analysis
        ComplexityAnalysis complexityAnalysis = performComplexityAnalysis(entities, queryPatterns, schema, dbSetMapping);

        result.setDenormalizationCandidates(candidates);
        result.setUsageProfiles(usageProfiles);
        result.setComplexityAnalysis(complexityAnalysis);
        result.setQueryPatterns(queryPatterns);

        log.info("Analysis complete. Found {} denormalization candidates", candidates.size());

        return result;
    }

    private Map<String, EntityUsageProfile> buildUsageProfiles(
            List<EntityModel> entities, List<QueryPattern> queryPatterns, Map<String, String> dbSetMapping, Map<String, Object> productionMetrics) {

        Map<String, EntityUsageProfile> profiles = new HashMap<>();

        // Initialize profiles for each entity
        for (EntityModel entity : entities) {
            profiles.put(entity.getClassName(), new EntityUsageProfile(entity));
        }
        
        // Store profiles as instance variable for access in createCandidate
        this.profiles = profiles;

        // Populate usage data from query patterns
        for (QueryPattern pattern : queryPatterns) {
            String entityName = extractEntityName(pattern.getTargetEntity(), dbSetMapping);
            EntityUsageProfile profile = profiles.get(entityName);

            if (profile != null) {
                profile.addQueryPattern(pattern);

                // Update read/write counts based on query type
                switch (pattern.getQueryType()) {
                    case SINGLE_ENTITY:
                    case COLLECTION:
                    case FILTERED_SINGLE:
                    case FILTERED_COLLECTION:
                        profile.setReadCount(profile.getReadCount() + pattern.getFrequency());
                        break;
                    case EAGER_LOADING:
                    case COMPLEX_EAGER_LOADING:
                        profile.incrementEagerLoadingCount(pattern.getFrequency());
                        profile.setReadCount(profile.getReadCount() + pattern.getFrequency());

                        // Extract related entities from eager loading
                        extractRelatedEntities(pattern, profile);
                        break;
                }
            }
        }
        
        // Integrate production metrics from Query Store if available
        if (productionMetrics != null) {
            integrateProductionMetrics(profiles, productionMetrics, dbSetMapping);
        }

        return profiles;
    }

    private String extractEntityName(String targetEntity, Map<String, String> dbSetMapping) {
        // Handle patterns like "Customer.Orders" or "Customer.nested.OrderItems"
        if (targetEntity.contains(".")) {
            return targetEntity.substring(0, targetEntity.indexOf("."));
        }
        
        // Use DbSet mapping to convert property name to entity name
        String mappedEntity = dbSetMapping.get(targetEntity);
        if (mappedEntity != null) {
            return mappedEntity;
        }
        
        // Fall back to original value if no mapping found
        return targetEntity;
    }

    private void extractRelatedEntities(QueryPattern pattern, EntityUsageProfile profile) {
        String targetEntity = pattern.getTargetEntity();

        if (targetEntity.contains(".")) {
            String[] parts = targetEntity.split("\\.");
            if (parts.length > 1) {
                String relatedEntity = parts[1];

                // Check if this entity is always loaded with the related one
                if (pattern.getFrequency() > thresholds.getMediumFrequencyThreshold()) {
                    if (!profile.getAlwaysLoadedWithEntities().contains(relatedEntity)) {
                        profile.getAlwaysLoadedWithEntities().add(relatedEntity);
                    }
                }
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void integrateProductionMetrics(Map<String, EntityUsageProfile> profiles, 
                                          Map<String, Object> productionMetrics,
                                          Map<String, String> dbSetMapping) {
        log.info("Integrating production metrics into entity usage profiles");
        
        // Extract qualified metrics
        Map<String, Object> qualifiedMetrics = (Map<String, Object>) productionMetrics.get("qualifiedMetrics");
        if (qualifiedMetrics == null) {
            log.warn("No qualified metrics found in production data");
            return;
        }
        
        // Create a map of table names to entity names using explicit mappings
        Map<String, String> tableToEntityMap = new HashMap<>();
        for (Map.Entry<String, EntityUsageProfile> entry : profiles.entrySet()) {
            String entityName = entry.getKey();
            EntityUsageProfile profile = entry.getValue();
            String tableName = profile.getEntity().getEffectiveTableName();
            tableToEntityMap.put(tableName, entityName);
            tableToEntityMap.put(tableName.toLowerCase(), entityName); // Case-insensitive fallback
            log.debug("Mapped table '{}' to entity '{}'", tableName, entityName);
        }
        
        // Extract table access patterns
        Map<String, Object> tablePatterns = (Map<String, Object>) qualifiedMetrics.get("tableAccessPatterns");
        if (tablePatterns != null) {
            List<Map<String, Object>> frequentCombinations = 
                (List<Map<String, Object>>) tablePatterns.get("frequentTableCombinations");
            
            if (frequentCombinations != null) {
                // Process co-accessed tables
                for (Map<String, Object> combo : frequentCombinations) {
                    List<String> tables = (List<String>) combo.get("tables");
                    Long executions = (Long) combo.get("totalExecutions");
                    
                    // Map table names to entity names using explicit mappings
                    for (String table : tables) {
                        String entityName = tableToEntityMap.get(table);
                        if (entityName == null) {
                            entityName = tableToEntityMap.get(table.toLowerCase());
                        }
                        
                        if (entityName != null) {
                            EntityUsageProfile profile = profiles.get(entityName);
                            if (profile != null) {
                                // Update production execution count
                                profile.setProductionExecutionCount(
                                    profile.getProductionExecutionCount() + executions);
                                
                                // Add co-accessed entities
                                for (String otherTable : tables) {
                                    if (!otherTable.equals(table)) {
                                        String otherEntity = tableToEntityMap.get(otherTable);
                                        if (otherEntity == null) {
                                            otherEntity = tableToEntityMap.get(otherTable.toLowerCase());
                                        }
                                        if (otherEntity != null) {
                                            profile.addCoAccessedEntity(otherEntity, executions.intValue());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Extract per-table metrics from analyzed queries
        List<Map<String, Object>> analyzedQueries = (List<Map<String, Object>>) productionMetrics.get("queries");
        if (analyzedQueries != null) {
            Map<String, Long> tableReadCounts = new HashMap<>();
            Map<String, Long> tableWriteCounts = new HashMap<>();
            
            for (Map<String, Object> query : analyzedQueries) {
                String operationType = (String) query.get("operationType");
                List<String> tables = (List<String>) query.get("tablesAccessed");
                Long execCount = (Long) query.get("executionCount");
                
                for (String table : tables) {
                    if ("SELECT".equals(operationType)) {
                        tableReadCounts.merge(table, execCount, Long::sum);
                    } else if ("INSERT".equals(operationType) || "UPDATE".equals(operationType) || 
                               "DELETE".equals(operationType)) {
                        tableWriteCounts.merge(table, execCount, Long::sum);
                    }
                }
            }
            
            // Update production read/write ratios in profiles
            for (Map.Entry<String, EntityUsageProfile> entry : profiles.entrySet()) {
                String entityName = entry.getKey();
                EntityUsageProfile profile = entry.getValue();
                String tableName = profile.getEntity().getEffectiveTableName();
                
                long reads = tableReadCounts.getOrDefault(tableName, 0L);
                long writes = tableWriteCounts.getOrDefault(tableName, 0L);
                
                // Also try case-insensitive match
                if (reads == 0 && writes == 0) {
                    for (Map.Entry<String, Long> readEntry : tableReadCounts.entrySet()) {
                        if (readEntry.getKey().equalsIgnoreCase(tableName)) {
                            reads = readEntry.getValue();
                            break;
                        }
                    }
                    for (Map.Entry<String, Long> writeEntry : tableWriteCounts.entrySet()) {
                        if (writeEntry.getKey().equalsIgnoreCase(tableName)) {
                            writes = writeEntry.getValue();
                            break;
                        }
                    }
                }
                
                if (writes > 0) {
                    profile.setProductionReadWriteRatio((double) reads / writes);
                } else if (reads > 0) {
                    profile.setProductionReadWriteRatio(reads);
                }
                
                log.debug("Entity '{}' with table '{}': reads={}, writes={}", 
                         entityName, tableName, reads, writes);
            }
        }
    }

    private void analyzeRelationshipPatterns(Map<String, EntityUsageProfile> profiles,
                                             DatabaseSchema schema,
                                             Map<String, Object> productionMetrics) {
        // First, identify actual relationships from the database schema
        for (Relationship relationship : schema.getRelationships()) {
            EntityUsageProfile fromProfile = profiles.get(relationship.getFromTable());
            EntityUsageProfile toProfile = profiles.get(relationship.getToTable());

            if (fromProfile != null && toProfile != null) {
                // Track the database relationships in the profiles
                fromProfile.addRelatedEntity(relationship.getToTable(), relationship.getType());
                toProfile.addRelatedEntity(relationship.getFromTable(), relationship.getType());
                
                log.debug("Added relationship: {} <-> {} ({})", 
                         relationship.getFromTable(), relationship.getToTable(), relationship.getType());
            }
        }
        
        // Then analyze Entity Framework navigation properties
        for (EntityUsageProfile profile : profiles.values()) {
            EntityModel entity = profile.getEntity();
            
            // Add entities referenced through navigation properties
            for (NavigationProperty navProp : entity.getNavigationProperties()) {
                String relatedEntity = navProp.getTargetEntity();
                EntityUsageProfile relatedProfile = profiles.get(relatedEntity);
                
                if (relatedProfile != null) {
                    profile.addRelatedEntity(relatedEntity, 
                            convertNavigationType(navProp.getType()));
                    
                    log.debug("Added navigation property relationship: {} -> {} ({})",
                            entity.getClassName(), relatedEntity, navProp.getType());
                }
            }
        }
        
        // Finally, integrate co-access patterns from Query Store (if available)
        if (productionMetrics != null) {
            integrateProductionCoAccessPatterns(profiles, productionMetrics);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void integrateProductionCoAccessPatterns(Map<String, EntityUsageProfile> profiles,
                                                   Map<String, Object> productionMetrics) {
        // The co-access patterns are already integrated in integrateProductionMetrics
        // but we can add additional relationship analysis here if needed
        
        // Extract qualified metrics
        Map<String, Object> qualifiedMetrics = (Map<String, Object>) productionMetrics.get("qualifiedMetrics");
        if (qualifiedMetrics == null) {
            return;
        }
        
        // Check for strong co-access patterns
        Map<String, Object> tablePatterns = (Map<String, Object>) qualifiedMetrics.get("tableAccessPatterns");
        if (tablePatterns != null) {
            Boolean hasStrongCoAccessPatterns = (Boolean) tablePatterns.get("hasStrongCoAccessPatterns");
            if (Boolean.TRUE.equals(hasStrongCoAccessPatterns)) {
                log.info("Found strong co-access patterns in production data - these will influence denormalization candidates");
            }
        }
    }
    
    private RelationshipType convertNavigationType(NavigationType navType) {
        switch (navType) {
            case ONE_TO_ONE:
                return RelationshipType.ONE_TO_ONE;
            case ONE_TO_MANY:
                return RelationshipType.ONE_TO_MANY;
            case MANY_TO_ONE:
                return RelationshipType.MANY_TO_ONE;
            case MANY_TO_MANY:
                return RelationshipType.MANY_TO_MANY;
            default:
                return RelationshipType.ONE_TO_MANY; // Default
        }
    }

    private List<DenormalizationCandidate> identifyDenormalizationCandidates(
            Map<String, EntityUsageProfile> profiles, DatabaseSchema schema) {

        List<DenormalizationCandidate> candidates = new ArrayList<>();

        for (EntityUsageProfile profile : profiles.values()) {
            if (isGoodDenormalizationCandidate(profile)) {
                DenormalizationCandidate candidate = createCandidate(profile, schema);
                candidates.add(candidate);

                log.debug("Identified denormalization candidate: {} with score {}",
                        candidate.getPrimaryEntity(), candidate.getScore());
            }
        }

        // Sort by score descending
        candidates.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

        return candidates;
    }

    private boolean isGoodDenormalizationCandidate(EntityUsageProfile profile) {
        // Rules for good denormalization candidates:
        
        // Check production metrics first if available
        if (profile.hasHighProductionUsage(thresholds.getHighProductionExecutionThreshold())) {
            log.trace("{} has high production usage: {}",
                    profile.getEntityName(), profile.getProductionExecutionCount());
            return true;
        }
        
        // High production co-access patterns
        if (!profile.getCoAccessedEntities().isEmpty() && 
            profile.getCoAccessedEntities().values().stream().anyMatch(count -> count > thresholds.getProductionCoAccessThreshold())) {
            log.trace("{} has high production co-access patterns", profile.getEntityName());
            return true;
        }

        // High frequency eager loading (use combined frequency)
        if (profile.getTotalAccessFrequency() > thresholds.getHighFrequencyThreshold()) {
            log.trace("{} has high total access frequency: {}",
                    profile.getEntityName(), profile.getTotalAccessFrequency());
            return true;
        }

        // Medium frequency with always loaded entities
        if (profile.getTotalAccessFrequency() > thresholds.getMediumFrequencyThreshold() &&
                !profile.getAlwaysLoadedWithEntities().isEmpty()) {
            log.trace("{} has medium frequency with related entities", profile.getEntityName());
            return true;
        }

        // Read-heavy patterns (use combined read/write ratio)
        if (profile.getCombinedReadWriteRatio() > thresholds.getHighReadWriteRatio() &&
                profile.getReadCount() > thresholds.getMediumFrequencyThreshold()) {
            log.trace("{} has high combined read/write ratio: {}",
                    profile.getEntityName(), profile.getCombinedReadWriteRatio());
            return true;
        }

        // Complex query patterns
        long complexQueries = profile.getQueryPatterns().stream()
                .filter(p -> p.getQueryType() == QueryType.COMPLEX_EAGER_LOADING)
                .count();

        if (complexQueries > thresholds.getComplexQueryRequirement()) {
            log.trace("{} has {} complex queries", profile.getEntityName(), complexQueries);
            return true;
        }

        return false;
    }

    private DenormalizationCandidate createCandidate(EntityUsageProfile profile,
                                                     DatabaseSchema schema) {
        // Collect all related entities from various sources
        Set<String> allRelatedEntities = new HashSet<>();
        
        // 1. Entities always loaded together (from query patterns)
        allRelatedEntities.addAll(profile.getAlwaysLoadedWithEntities());
        
        log.debug("Creating candidate for {}: alwaysLoaded={}, relatedEntities={}",
                 profile.getEntityName(), profile.getAlwaysLoadedWithEntities(), 
                 profile.getRelatedEntities());
        
        // 2. Entities with strong relationships (from database schema & EF navigation)
        for (Map.Entry<String, RelationshipType> entry : profile.getRelatedEntities().entrySet()) {
            String relatedEntity = entry.getKey();
            RelationshipType relType = entry.getValue();
            
            // Include entities with strong relationships (1:1 or frequently accessed 1:N)
            if (relType == RelationshipType.ONE_TO_ONE) {
                allRelatedEntities.add(relatedEntity);
            } else if (relType == RelationshipType.ONE_TO_MANY || 
                      relType == RelationshipType.MANY_TO_ONE) {
                // Check if this related entity is frequently accessed
                EntityUsageProfile relatedProfile = profiles.get(relatedEntity);
                if (relatedProfile != null && 
                    (profile.getAlwaysLoadedWithEntities().contains(relatedEntity) ||
                     relatedProfile.getAlwaysLoadedWithEntities().contains(profile.getEntityName()))) {
                    allRelatedEntities.add(relatedEntity);
                }
            }
        }
        
        // 3. Entities with high co-access patterns from production (Query Store)
        for (Map.Entry<String, Integer> entry : profile.getCoAccessedEntities().entrySet()) {
            String coAccessedEntity = entry.getKey();
            Integer coAccessCount = entry.getValue();
            
            // Include entities that are frequently co-accessed in production
            if (coAccessCount > thresholds.getProductionCoAccessThreshold()) {
                allRelatedEntities.add(coAccessedEntity);
                log.debug("Adding {} to related entities due to high production co-access count: {}",
                         coAccessedEntity, coAccessCount);
            }
        }
        
        return DenormalizationCandidate.builder()
                .primaryEntity(profile.getEntityName())
                .relatedEntities(new ArrayList<>(allRelatedEntities))
                .complexity(calculateComplexity(profile, schema))
                .reason(generateReason(profile))
                .score(calculateScore(profile))
                .recommendedTarget(selectOptimalTarget(profile))
                .build();
    }

    private MigrationComplexity calculateComplexity(EntityUsageProfile profile, DatabaseSchema schema) {
        int complexityScore = 0;

        // Factor in number of relationships
        EntityModel entity = profile.getEntity();
        complexityScore += entity.getNavigationProperties().size() * 5;

        // Factor in circular references
        if (entity.hasCircularReferences()) {
            complexityScore += (int) (thresholds.getComplexRelationshipPenalty() * 2 * thresholds.getComplexityPenaltyMultiplier());
        }

        // Factor in number of always-loaded entities
        complexityScore += profile.getAlwaysLoadedWithEntities().size() * 3;

        // Factor in query complexity
        long complexQueries = profile.getQueryPatterns().stream()
                .filter(p -> p.getQueryType() == QueryType.COMPLEX_EAGER_LOADING)
                .count();
        complexityScore += (int) (complexQueries * 4);

        // Check for many-to-many relationships
        long manyToManyCount = entity.getNavigationProperties().stream()
                .filter(p -> p.getType() == NavigationType.MANY_TO_MANY)
                .count();
        complexityScore += (int) (manyToManyCount * 10);

        // Factor in database schema complexity
        if (schema != null) {
            String entityName = entity.getClassName();
            
            // Find corresponding table
            Table table = schema.findTable(entityName);
            if (table != null) {
                // More columns = more complexity
                complexityScore += Math.min(table.getColumns().size(), 20);
                
                // Multiple indexes suggest complex access patterns
                complexityScore += table.getIndexes().size() * 2;
            }
            
            // Number of relationships involving this entity
            long relationshipCount = schema.getRelationships().stream()
                    .filter(r -> r.getFromTable().equalsIgnoreCase(entityName) || 
                               r.getToTable().equalsIgnoreCase(entityName))
                    .count();
            complexityScore += (int) (relationshipCount * 3);
        }

        return MigrationComplexity.fromScore(complexityScore);
    }

    private String generateReason(EntityUsageProfile profile) {
        List<String> reasons = new ArrayList<>();

        if (profile.getEagerLoadingCount() > thresholds.getHighFrequencyThreshold()) {
            reasons.add("High frequency eager loading (" + profile.getEagerLoadingCount() + " occurrences)");
        }

        if (!profile.getAlwaysLoadedWithEntities().isEmpty()) {
            reasons.add("Always loaded with: " + String.join(", ", profile.getAlwaysLoadedWithEntities()));
        }

        if (profile.getReadToWriteRatio() > thresholds.getHighReadWriteRatio()) {
            reasons.add("Read-heavy access pattern (ratio: " +
                    String.format("%.1f", profile.getReadToWriteRatio()) + ":1)");
        }

        long complexQueries = profile.getQueryPatterns().stream()
                .filter(p -> p.getQueryType() == QueryType.COMPLEX_EAGER_LOADING)
                .count();

        if (complexQueries > 0) {
            reasons.add("Complex eager loading patterns (" + complexQueries + " complex queries)");
        }

        return String.join("; ", reasons);
    }

    private int calculateScore(EntityUsageProfile profile) {
        int score = 0;

        // Production metrics contribute significantly if available (max 50 points)
        if (profile.getProductionExecutionCount() > 0) {
            // Scale production execution count (max 30 points)
            long normalizedExecution = profile.getProductionExecutionCount() / 100;
            score += (int) Math.min(normalizedExecution, 30);
            
            // Co-access patterns (max 20 points)
            int coAccessScore = profile.getCoAccessedEntities().values().stream()
                .mapToInt(count -> count > thresholds.getProductionCoAccessThreshold() ? 5 : 0)
                .sum();
            score += Math.min(coAccessScore, 20);
        }

        // Total access frequency (combines eager loading + production) (max 100 points)
        score += Math.min(profile.getTotalAccessFrequency(), 100);

        // Always loaded entities (max 30 points - 10 per entity, up to 3)
        score += Math.min(profile.getAlwaysLoadedWithEntities().size() * 10, 30);

        // Combined read/write ratio (max 20 points)
        if (profile.getCombinedReadWriteRatio() > thresholds.getHighReadWriteRatio()) {
            score += 20;
        } else if (profile.getCombinedReadWriteRatio() > thresholds.getHighReadWriteRatio() / 2) {
            score += 10;
        }

        // Query complexity (max 25 points - 5 per complex query, up to 5)
        long complexQueries = profile.getQueryPatterns().stream()
                .filter(p -> p.getQueryType() == QueryType.COMPLEX_EAGER_LOADING)
                .count();
        score += (int) Math.min(complexQueries * 5, 25);

        // Bonus for simple key-based access (15 points)
        if (profile.hasSimpleKeyBasedAccess()) {
            score += 15;
        }

        // Penalty for complex relationships (up to -20 points)
        if (profile.hasComplexRelationships()) {
            score -= 10;
        }

        if (profile.getEntity().hasCircularReferences()) {
            score -= 10;
        }

        // Maximum theoretical score: 100 + 30 + 20 + 25 + 15 = 190
        // Minimum score: 0 (ensured below)
        return Math.max(score, 0);
    }


    private NoSQLTarget selectOptimalTarget(EntityUsageProfile profile) {
        EntityModel entity = profile.getEntity();

        // Document-like structures with nested relationships
        if (profile.getAlwaysLoadedWithEntities().size() > 2 ||
                hasDeepNesting(profile)) {
            return NoSQLTarget.DOCUMENTDB;
        }

        // Complex many-to-many relationships or graph-like structures
        if (hasComplexManyToMany(entity) || hasGraphPattern(profile)) {
            return NoSQLTarget.NEPTUNE;
        }

        // Simple key-value with predictable access patterns
        if (profile.hasSimpleKeyBasedAccess() &&
                profile.getAlwaysLoadedWithEntities().size() <= 1) {
            return NoSQLTarget.DYNAMODB;
        }

        // Default to DynamoDB for most cases (single table design can handle many patterns)
        return NoSQLTarget.DYNAMODB;
    }

    private boolean hasDeepNesting(EntityUsageProfile profile) {
        // Check for nested eager loading patterns
        return profile.getQueryPatterns().stream()
                .anyMatch(p -> p.getTargetEntity().contains(".nested.") ||
                        p.getType().equals("NESTED_EAGER_LOADING"));
    }

    private boolean hasComplexManyToMany(EntityModel entity) {
        return entity.getNavigationProperties().stream()
                .filter(p -> p.getType() == NavigationType.MANY_TO_MANY)
                .count() > 1;
    }

    private boolean hasGraphPattern(EntityUsageProfile profile) {
        // Simple heuristic: circular references or many interconnected entities
        return profile.getEntity().hasCircularReferences() ||
                profile.getAlwaysLoadedWithEntities().size() > 3;
    }

    private ComplexityAnalysis performComplexityAnalysis(List<EntityModel> entities,
                                                         List<QueryPattern> queryPatterns,
                                                         DatabaseSchema schema,
                                                         Map<String, String> dbSetMapping) {
        Map<String, Integer> entityComplexityScores = new HashMap<>();
        int totalComplexity = 0;

        for (EntityModel entity : entities) {
            int score = calculateEntityComplexity(entity, queryPatterns, schema, dbSetMapping);
            entityComplexityScores.put(entity.getClassName(), score);
            totalComplexity += score;
        }

        String complexityReason = generateOverallComplexityReason(
                entityComplexityScores, queryPatterns, schema);

        return ComplexityAnalysis.builder()
                .entityComplexityScores(entityComplexityScores)
                .overallComplexity(totalComplexity)
                .complexityReason(complexityReason)
                .build();
    }

    private int calculateEntityComplexity(EntityModel entity,
                                          List<QueryPattern> queryPatterns,
                                          DatabaseSchema schema,
                                          Map<String, String> dbSetMapping) {
        int complexity = 0;

        // Navigation properties
        complexity += entity.getNavigationProperties().size() * 2;

        // Circular references
        if (entity.hasCircularReferences()) {
            complexity += 20;
        }

        // Query patterns involving this entity
        long entityQueries = queryPatterns.stream()
                .filter(p -> extractEntityName(p.getTargetEntity(), dbSetMapping).equals(entity.getClassName()))
                .count();
        complexity += (int) entityQueries;

        // Database relationships
        long relationships = schema.getRelationships().stream()
                .filter(r -> r.getFromTable().equals(entity.getClassName()) ||
                        r.getToTable().equals(entity.getClassName()))
                .count();
        complexity += (int) (relationships * 3);

        return complexity;
    }

    private String generateOverallComplexityReason(Map<String, Integer> scores,
                                                   List<QueryPattern> queryPatterns,
                                                   DatabaseSchema schema) {
        List<String> reasons = new ArrayList<>();

        // Find most complex entities
        List<Map.Entry<String, Integer>> sortedScores = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .toList();

        if (!sortedScores.isEmpty()) {
            reasons.add("Most complex entities: " +
                    sortedScores.stream()
                            .map(e -> e.getKey() + " (" + e.getValue() + ")")
                            .collect(Collectors.joining(", ")));
        }

        // Overall statistics
        long totalRelationships = schema.getRelationships().size();
        long complexQueries = queryPatterns.stream()
                .filter(p -> p.getQueryType() == QueryType.COMPLEX_EAGER_LOADING)
                .count();

        reasons.add("Total relationships: " + totalRelationships);
        reasons.add("Complex query patterns: " + complexQueries);

        return String.join("; ", reasons);
    }
}