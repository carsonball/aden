package org.carball.aden.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.carball.aden.model.analysis.*;
import org.carball.aden.model.entity.EntityModel;
import org.carball.aden.model.entity.NavigationType;
import org.carball.aden.model.query.QueryPattern;
import org.carball.aden.model.query.QueryType;
import org.carball.aden.model.schema.DatabaseSchema;
import org.carball.aden.model.schema.Relationship;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DotNetPatternAnalyzer {

    private static final int HIGH_FREQUENCY_THRESHOLD = 50;
    private static final int MEDIUM_FREQUENCY_THRESHOLD = 20;
    private static final int COMPLEX_RELATIONSHIP_PENALTY = 10;
    private static final double HIGH_READ_WRITE_RATIO = 10.0;

    public AnalysisResult analyzePatterns(List<EntityModel> entities,
                                          List<QueryPattern> queryPatterns,
                                          DatabaseSchema schema) {

        log.info("Analyzing patterns for {} entities and {} query patterns",
                entities.size(), queryPatterns.size());

        AnalysisResult result = new AnalysisResult();

        // Build usage profiles
        Map<String, EntityUsageProfile> usageProfiles = buildUsageProfiles(entities, queryPatterns);

        // Analyze relationships and access patterns
        analyzeRelationshipPatterns(usageProfiles, schema);

        // Identify denormalization candidates
        List<DenormalizationCandidate> candidates = identifyDenormalizationCandidates(
                usageProfiles, schema);

        // Perform complexity analysis
        ComplexityAnalysis complexityAnalysis = performComplexityAnalysis(entities, queryPatterns, schema);

        result.setDenormalizationCandidates(candidates);
        result.setUsageProfiles(usageProfiles);
        result.setComplexityAnalysis(complexityAnalysis);
        result.setQueryPatterns(queryPatterns);

        log.info("Analysis complete. Found {} denormalization candidates", candidates.size());

        return result;
    }

    private Map<String, EntityUsageProfile> buildUsageProfiles(
            List<EntityModel> entities, List<QueryPattern> queryPatterns) {

        Map<String, EntityUsageProfile> profiles = new HashMap<>();

        // Initialize profiles for each entity
        for (EntityModel entity : entities) {
            profiles.put(entity.getClassName(), new EntityUsageProfile(entity));
        }

        // Populate usage data from query patterns
        for (QueryPattern pattern : queryPatterns) {
            String entityName = extractEntityName(pattern.getTargetEntity());
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

        return profiles;
    }

    private String extractEntityName(String targetEntity) {
        // Handle patterns like "Customer.Orders" or "Customer.nested.OrderItems"
        if (targetEntity.contains(".")) {
            return targetEntity.substring(0, targetEntity.indexOf("."));
        }
        return targetEntity;
    }

    private void extractRelatedEntities(QueryPattern pattern, EntityUsageProfile profile) {
        String targetEntity = pattern.getTargetEntity();

        if (targetEntity.contains(".")) {
            String[] parts = targetEntity.split("\\.");
            if (parts.length > 1) {
                String relatedEntity = parts[1];

                // Check if this entity is always loaded with the related one
                if (pattern.getFrequency() > MEDIUM_FREQUENCY_THRESHOLD) {
                    if (!profile.getAlwaysLoadedWithEntities().contains(relatedEntity)) {
                        profile.getAlwaysLoadedWithEntities().add(relatedEntity);
                    }
                }
            }
        }
    }

    private void analyzeRelationshipPatterns(Map<String, EntityUsageProfile> profiles,
                                             DatabaseSchema schema) {
        // Analyze database relationships to understand access patterns
        for (Relationship relationship : schema.getRelationships()) {
            EntityUsageProfile fromProfile = profiles.get(relationship.getFromTable());
            EntityUsageProfile toProfile = profiles.get(relationship.getToTable());

            if (fromProfile != null && toProfile != null) {
                // Check if these entities are frequently accessed together
                boolean frequentlyTogether = fromProfile.getAlwaysLoadedWithEntities()
                        .contains(relationship.getToTable()) ||
                        toProfile.getAlwaysLoadedWithEntities()
                                .contains(relationship.getFromTable());

                if (frequentlyTogether) {
                    log.debug("Entities {} and {} are frequently accessed together",
                            relationship.getFromTable(), relationship.getToTable());
                }
            }
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

        // High frequency eager loading
        if (profile.getEagerLoadingCount() > HIGH_FREQUENCY_THRESHOLD) {
            log.trace("{} has high eager loading count: {}",
                    profile.getEntityName(), profile.getEagerLoadingCount());
            return true;
        }

        // Medium frequency with always loaded entities
        if (profile.getEagerLoadingCount() > MEDIUM_FREQUENCY_THRESHOLD &&
                !profile.getAlwaysLoadedWithEntities().isEmpty()) {
            log.trace("{} has medium eager loading with related entities", profile.getEntityName());
            return true;
        }

        // Read-heavy patterns
        if (profile.getReadToWriteRatio() > HIGH_READ_WRITE_RATIO &&
                profile.getReadCount() > MEDIUM_FREQUENCY_THRESHOLD) {
            log.trace("{} has high read/write ratio: {}",
                    profile.getEntityName(), profile.getReadToWriteRatio());
            return true;
        }

        // Complex query patterns
        long complexQueries = profile.getQueryPatterns().stream()
                .filter(p -> p.getQueryType() == QueryType.COMPLEX_EAGER_LOADING)
                .count();

        if (complexQueries > 5) {
            log.trace("{} has {} complex queries", profile.getEntityName(), complexQueries);
            return true;
        }

        return false;
    }

    private DenormalizationCandidate createCandidate(EntityUsageProfile profile,
                                                     DatabaseSchema schema) {
        DenormalizationCandidate candidate = new DenormalizationCandidate();
        candidate.setPrimaryEntity(profile.getEntityName());
        candidate.setRelatedEntities(new ArrayList<>(profile.getAlwaysLoadedWithEntities()));
        candidate.setComplexity(calculateComplexity(profile, schema));
        candidate.setReason(generateReason(profile));
        candidate.setScore(calculateScore(profile));
        candidate.setRecommendedTarget(selectOptimalTarget(profile));

        return candidate;
    }

    private MigrationComplexity calculateComplexity(EntityUsageProfile profile, DatabaseSchema schema) {
        int complexityScore = 0;

        // Factor in number of relationships
        EntityModel entity = profile.getEntity();
        complexityScore += entity.getNavigationProperties().size() * 5;

        // Factor in circular references
        if (entity.hasCircularReferences()) {
            complexityScore += COMPLEX_RELATIONSHIP_PENALTY * 2;
        }

        // Factor in number of always-loaded entities
        complexityScore += profile.getAlwaysLoadedWithEntities().size() * 3;

        // Factor in query complexity
        long complexQueries = profile.getQueryPatterns().stream()
                .filter(p -> p.getQueryType() == QueryType.COMPLEX_EAGER_LOADING)
                .count();
        complexityScore += complexQueries * 4;

        // Check for many-to-many relationships
        long manyToManyCount = entity.getNavigationProperties().stream()
                .filter(p -> p.getType() == NavigationType.MANY_TO_MANY)
                .count();
        complexityScore += manyToManyCount * 10;

        return MigrationComplexity.fromScore(complexityScore);
    }

    private String generateReason(EntityUsageProfile profile) {
        List<String> reasons = new ArrayList<>();

        if (profile.getEagerLoadingCount() > HIGH_FREQUENCY_THRESHOLD) {
            reasons.add("High frequency eager loading (" + profile.getEagerLoadingCount() + " occurrences)");
        }

        if (!profile.getAlwaysLoadedWithEntities().isEmpty()) {
            reasons.add("Always loaded with: " + String.join(", ", profile.getAlwaysLoadedWithEntities()));
        }

        if (profile.getReadToWriteRatio() > HIGH_READ_WRITE_RATIO) {
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

        // Eager loading frequency contributes most to score
        score += Math.min(profile.getEagerLoadingCount(), 100);

        // Always loaded entities
        score += profile.getAlwaysLoadedWithEntities().size() * 10;

        // Read/write ratio
        if (profile.getReadToWriteRatio() > HIGH_READ_WRITE_RATIO) {
            score += 20;
        }

        // Query complexity
        long complexQueries = profile.getQueryPatterns().stream()
                .filter(p -> p.getQueryType() == QueryType.COMPLEX_EAGER_LOADING)
                .count();
        score += complexQueries * 5;

        // Bonus for simple key-based access
        if (profile.hasSimpleKeyBasedAccess()) {
            score += 15;
        }

        // Penalty for complex relationships
        if (profile.hasComplexRelationships()) {
            score -= 10;
        }

        return Math.max(score, 0); // Ensure non-negative score
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
                                                         DatabaseSchema schema) {
        Map<String, Integer> entityComplexityScores = new HashMap<>();
        int totalComplexity = 0;

        for (EntityModel entity : entities) {
            int score = calculateEntityComplexity(entity, queryPatterns, schema);
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
                                          DatabaseSchema schema) {
        int complexity = 0;

        // Navigation properties
        complexity += entity.getNavigationProperties().size() * 2;

        // Circular references
        if (entity.hasCircularReferences()) {
            complexity += 20;
        }

        // Query patterns involving this entity
        long entityQueries = queryPatterns.stream()
                .filter(p -> extractEntityName(p.getTargetEntity()).equals(entity.getClassName()))
                .count();
        complexity += entityQueries;

        // Database relationships
        long relationships = schema.getRelationships().stream()
                .filter(r -> r.getFromTable().equals(entity.getClassName()) ||
                        r.getToTable().equals(entity.getClassName()))
                .count();
        complexity += relationships * 3;

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
                .collect(Collectors.toList());

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