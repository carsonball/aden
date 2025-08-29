package org.carball.aden.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.carball.aden.config.ThresholdConfig;
import org.carball.aden.model.analysis.*;
import org.carball.aden.model.entity.EntityModel;
import org.carball.aden.model.entity.NavigationProperty;
import org.carball.aden.model.entity.NavigationType;
import org.carball.aden.model.query.*;
import org.carball.aden.model.query.QueryType;
import org.carball.aden.model.schema.DatabaseSchema;
import org.carball.aden.model.schema.Relationship;
import org.carball.aden.model.schema.RelationshipType;
import org.carball.aden.model.schema.Table;

import java.util.*;

@Slf4j
public class DotNetPatternAnalyzer {

    private final ThresholdConfig thresholds;
    private Map<String, EntityUsageProfile> profiles; // Instance variable for profile access
    
    public DotNetPatternAnalyzer() {
        this(ThresholdConfig.createDiscoveryDefaults());
    }
    
    public DotNetPatternAnalyzer(ThresholdConfig thresholds) {
        this.thresholds = thresholds;
        log.debug("Initialized DotNetPatternAnalyzer with thresholds: {}", thresholds.getDescription());
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
                                          QueryStoreAnalysis productionMetrics) {

        log.info("Analyzing patterns for {} entities and {} query patterns",
                entities.size(), queryPatterns.size());

        // Build usage profiles
        Map<String, EntityUsageProfile> usageProfiles = buildUsageProfiles(entities, queryPatterns, dbSetMapping, productionMetrics);

        // Analyze relationships and access patterns
        analyzeRelationshipPatterns(usageProfiles, schema, productionMetrics);

        // Identify denormalization candidates
        List<DenormalizationCandidate> candidates = identifyDenormalizationCandidates(
                usageProfiles, schema);

        log.info("Analysis complete. Found {} denormalization candidates", candidates.size());

        return new AnalysisResult(candidates, usageProfiles, queryPatterns);
    }

    private Map<String, EntityUsageProfile> buildUsageProfiles(
            List<EntityModel> entities, List<QueryPattern> queryPatterns, Map<String, String> dbSetMapping, QueryStoreAnalysis productionMetrics) {

        Map<String, EntityUsageProfile> profiles = new HashMap<>();

        // Initialize profiles for each entity
        for (EntityModel entity : entities) {
            profiles.put(entity.getClassName(), new EntityUsageProfile(entity));
        }

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
                        profile.setReadCount(profile.getReadCount() + 1);
                        break;
                    case EAGER_LOADING:
                    case COMPLEX_EAGER_LOADING:
                        profile.incrementEagerLoadingCount(1);
                        profile.setReadCount(profile.getReadCount() + 1);

                        // Extract related entities from eager loading
                        extractRelatedEntities(pattern, profile);
                        
                        // Process joined entities from Include patterns
                        processJoinedEntities(pattern, profile);
                        break;
                }
            }
        }
        
        // Integrate production metrics from Query Store if available
        if (productionMetrics != null) {
            integrateProductionMetrics(profiles, productionMetrics);
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

                if (!profile.getAlwaysLoadedWithEntities().contains(relatedEntity)) {
                    profile.getAlwaysLoadedWithEntities().add(relatedEntity);
                }
            }
        }
    }
    
    /**
     * Processes joined entities from Include patterns and adds them to the profile's
     * always loaded entities, since Include patterns represent explicit co-access.
     */
    private void processJoinedEntities(QueryPattern pattern, EntityUsageProfile profile) {
        if (pattern.hasJoins()) {
            for (String joinedEntity : pattern.getJoinedEntities()) {
                if (!profile.getAlwaysLoadedWithEntities().contains(joinedEntity)) {
                    profile.getAlwaysLoadedWithEntities().add(joinedEntity);
                    log.debug("Added joined entity '{}' to profile for '{}' from Include pattern", 
                             joinedEntity, profile.getEntityName());
                }
            }
        }
    }
    
    private void integrateProductionMetrics(Map<String, EntityUsageProfile> profiles, 
                                          QueryStoreAnalysis productionMetrics) {
        log.info("Integrating production metrics into entity usage profiles");
        
        // Extract query store metrics
        QueryStoreMetrics queryStoreMetrics = productionMetrics.queryStoreMetrics();
        if (queryStoreMetrics == null) {
            log.warn("No query store metrics found in production data");
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
        TableAccessPatterns tablePatterns = queryStoreMetrics.getTableAccessPatterns();
        if (tablePatterns != null) {
            List<TableCombination> frequentCombinations = 
                tablePatterns.frequentTableCombinations();
            
            if (frequentCombinations != null) {
                processTableCombinations(frequentCombinations, tableToEntityMap, profiles);
            }
        }
        
        // Extract per-table metrics from analyzed queries
        List<AnalyzedQuery> analyzedQueries = productionMetrics.queries();
        if (analyzedQueries != null) {
            Map<String, Long> tableReadCounts = new HashMap<>();
            Map<String, Long> tableWriteCounts = new HashMap<>();

            calculateTableReadWriteCounts(analyzedQueries, tableReadCounts, tableWriteCounts);

            // Update production read/write ratios in profiles
            calculateReadWriteRatios(profiles, tableReadCounts, tableWriteCounts);
        }
    }

    private static void calculateReadWriteRatios(Map<String, EntityUsageProfile> profiles, Map<String, Long> tableReadCounts, Map<String, Long> tableWriteCounts) {
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

    private static void calculateTableReadWriteCounts(List<AnalyzedQuery> analyzedQueries, Map<String, Long> tableReadCounts, Map<String, Long> tableWriteCounts) {
        for (AnalyzedQuery query : analyzedQueries) {
            String operationType = query.getOperationType();
            List<String> tables = query.getTablesAccessed();
            long execCount = query.getExecutionCount();
            
            for (String table : tables) {
                if ("SELECT".equals(operationType)) {
                    tableReadCounts.merge(table, execCount, Long::sum);
                } else if ("INSERT".equals(operationType) || "UPDATE".equals(operationType) || 
                           "DELETE".equals(operationType)) {
                    tableWriteCounts.merge(table, execCount, Long::sum);
                }
            }
        }
    }

    private void analyzeRelationshipPatterns(Map<String, EntityUsageProfile> profiles,
                                             DatabaseSchema schema,
                                             QueryStoreAnalysis productionMetrics) {
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
            integrateProductionCoAccessPatterns(productionMetrics);
        }
    }
    
    /**
     * Processes table combinations from Query Store metrics, updating production execution counts
     * and co-access patterns.
     */
    private void processTableCombinations(List<TableCombination> frequentCombinations,
                                        Map<String, String> tableToEntityMap,
                                        Map<String, EntityUsageProfile> profiles) {
        for (TableCombination combo : frequentCombinations) {
            List<String> tables = combo.getTables();
            long executions = combo.getTotalExecutions();
            
            // First pass: update execution counts for all tables in combination
            for (String table : tables) {
                String entityName = tableToEntityMap.get(table);
                if (entityName == null) {
                    entityName = tableToEntityMap.get(table.toLowerCase());
                }
                
                if (entityName != null) {
                    EntityUsageProfile profile = profiles.get(entityName);
                    if (profile != null) {
                        profile.setProductionExecutionCount(
                            profile.getProductionExecutionCount() + executions);
                    }
                }
            }
            
            // Second pass: record co-access relationships
            for (String table : tables) {
                String entityName = tableToEntityMap.get(table);
                if (entityName == null) {
                    entityName = tableToEntityMap.get(table.toLowerCase());
                }
                
                if (entityName != null) {
                    EntityUsageProfile profile = profiles.get(entityName);
                    if (profile != null) {
                        for (String otherTable : tables) {
                            if (!otherTable.equals(table)) {
                                String otherEntity = tableToEntityMap.get(otherTable);
                                if (otherEntity == null) {
                                    otherEntity = tableToEntityMap.get(otherTable.toLowerCase());
                                }
                                if (otherEntity != null) {
                                    profile.addCoAccessedEntity(otherEntity, (int) executions);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void integrateProductionCoAccessPatterns(QueryStoreAnalysis productionMetrics) {
        // The co-access patterns are already integrated in integrateProductionMetrics
        // but we can add additional relationship analysis here if needed
        
        // Extract query store metrics
        QueryStoreMetrics queryStoreMetrics = productionMetrics.queryStoreMetrics();
        if (queryStoreMetrics == null) {
            return;
        }
        
        // Check for strong co-access patterns
        TableAccessPatterns tablePatterns = queryStoreMetrics.getTableAccessPatterns();
        if (tablePatterns != null) {
            boolean hasStrongCoAccessPatterns = tablePatterns.hasStrongCoAccessPatterns();
            if (hasStrongCoAccessPatterns) {
                log.info("Found strong co-access patterns in production data - these will influence denormalization candidates");
            }
        }
    }
    
    private RelationshipType convertNavigationType(NavigationType navType) {
        return switch (navType) {
            case ONE_TO_ONE -> RelationshipType.ONE_TO_ONE;
            case ONE_TO_MANY -> RelationshipType.ONE_TO_MANY;
            case MANY_TO_ONE -> RelationshipType.MANY_TO_ONE;
            case MANY_TO_MANY -> RelationshipType.MANY_TO_MANY;
            // Default
        };
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
        if (profile.hasHighProductionUsage(thresholds.getHighProductionUsageThreshold())) {
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

        // Any eager loading makes it a candidate
        if (profile.getEagerLoadingCount() > 0) {
            log.trace("{} has eager loading patterns: {}",
                    profile.getEntityName(), profile.getEagerLoadingCount());
            return true;
        }

        // Read-heavy patterns (use combined read/write ratio)
        if (profile.getCombinedReadWriteRatio() > thresholds.getReadHeavyRatioThreshold() &&
                profile.getReadCount() > thresholds.getMinReadCountForRatioAnalysis()) {
            log.trace("{} has high combined read/write ratio: {}",
                    profile.getEntityName(), profile.getCombinedReadWriteRatio());
            return true;
        }

        // Complex query patterns
        long complexQueries = profile.getQueryPatterns().stream()
                .filter(p -> p.getQueryType() == QueryType.COMPLEX_EAGER_LOADING)
                .count();

        if (complexQueries > 0) {
            log.trace("{} has {} complex queries", profile.getEntityName(), complexQueries);
            return true;
        }

        return false;
    }

    private DenormalizationCandidate createCandidate(EntityUsageProfile profile,
                                                     DatabaseSchema schema) {
        // Collect all related entities from various sources

        // 1. Entities always loaded together (from query patterns)
        Set<String> allRelatedEntities = new HashSet<>(profile.getAlwaysLoadedWithEntities());
        
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
            if (coAccessCount > thresholds.getRelatedEntityCoAccessThreshold()) {
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
            complexityScore += 40;
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

        if (profile.getEagerLoadingCount() > 0) {
            reasons.add("Eager loading patterns (" + profile.getEagerLoadingCount() + " occurrences)");
        }

        if (!profile.getAlwaysLoadedWithEntities().isEmpty()) {
            reasons.add("Always loaded with: " + String.join(", ", profile.getAlwaysLoadedWithEntities()));
        }

        if (profile.getReadToWriteRatio() > thresholds.getReadHeavyRatioThreshold()) {
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
                .mapToInt(count -> count > thresholds.getCoAccessScoringThreshold() ? 5 : 0)
                .sum();
            score += Math.min(coAccessScore, 20);
        }

        // Total access frequency (combines eager loading + production) (max 100 points)
        score += Math.min(profile.getTotalAccessFrequency(), 100);

        // Always loaded entities (max 30 points - 10 per entity, up to 3)
        score += Math.min(profile.getAlwaysLoadedWithEntities().size() * 10, 30);

        // Combined read/write ratio (max 20 points)
        if (profile.getCombinedReadWriteRatio() > thresholds.getReadHeavyRatioThreshold()) {
            score += 20;
        } else if (profile.getCombinedReadWriteRatio() > thresholds.getMediumReadRatioThreshold()) {
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


}