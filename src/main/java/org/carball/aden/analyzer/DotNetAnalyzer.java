package org.carball.aden.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.carball.aden.ai.RecommendationEngine;
import org.carball.aden.config.*;
import org.carball.aden.model.analysis.AnalysisResult;
import org.carball.aden.model.analysis.DenormalizationCandidate;
import org.carball.aden.model.entity.EntityModel;
import org.carball.aden.model.query.QueryPattern;
import org.carball.aden.model.recommendation.NoSQLRecommendation;
import org.carball.aden.model.schema.DatabaseSchema;
import org.carball.aden.parser.EFModelParser;
import org.carball.aden.parser.LinqAnalyzer;
import org.carball.aden.parser.SchemaParser;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class DotNetAnalyzer {

    private final DotNetAnalyzerConfig config;
    private final SchemaParser schemaParser;
    private final EFModelParser efParser;
    private final LinqAnalyzer linqAnalyzer;
    private final DotNetPatternAnalyzer patternAnalyzer;
    private final RecommendationEngine recommendationEngine;
    private DatabaseSchema currentSchema; // Store schema for recommendation generation

    public DotNetAnalyzer(DotNetAnalyzerConfig config) {
        this(config, new String[0]);
    }
    
    public DotNetAnalyzer(DotNetAnalyzerConfig config, String[] args) {
        this.config = config;
        this.schemaParser = new SchemaParser();
        this.efParser = new EFModelParser();
        this.linqAnalyzer = new LinqAnalyzer();
        
        // Load migration thresholds
        MigrationThresholds thresholds = loadMigrationThresholds(config, args);
        this.patternAnalyzer = new DotNetPatternAnalyzer(thresholds);
        this.recommendationEngine = new RecommendationEngine(config.getOpenAiApiKey());

        log.info("Initialized DotNetAnalyzer with config: {}", config);
    }
    
    private MigrationThresholds loadMigrationThresholds(DotNetAnalyzerConfig config, String[] args) {
        ConfigurationLoader loader = new ConfigurationLoader();
        
        if (config.getMigrationProfile() != null) {
            return loader.loadConfigurationWithProfile(
                config.getMigrationProfile(), 
                args
            );
        } else {
            return loader.loadConfiguration(args);
        }
    }

    public AnalysisResult analyze() throws IOException {
        log.info("Starting analysis of .NET Framework application");

        // Step 1: Parse database schema
        if (config.isVerbose()) {
            System.out.println("  - Parsing database schema...");
        }
        DatabaseSchema schema = schemaParser.parseDDL(config.getSchemaFile());
        this.currentSchema = schema; // Store for later use in recommendations
        log.info("Parsed {} tables and {} relationships from schema",
                schema.getTables().size(), schema.getRelationships().size());

        // Step 2: Parse Entity Framework models
        if (config.isVerbose()) {
            System.out.println("  - Parsing Entity Framework models...");
        }
        List<EntityModel> entities = efParser.parseEntities(config.getSourceDirectory());
        log.info("Found {} Entity Framework models", entities.size());

        // Step 3: Analyze LINQ query patterns
        if (config.isVerbose()) {
            System.out.println("  - Analyzing LINQ query patterns...");
        }
        List<QueryPattern> queryPatterns = linqAnalyzer.analyzeLinqPatterns(config.getSourceDirectory());
        log.info("Identified {} unique query patterns", queryPatterns.size());

        // Step 4: Correlate patterns and generate analysis
        if (config.isVerbose()) {
            System.out.println("  - Correlating patterns and scoring complexity...");
        }
        Map<String, String> dbSetMapping = efParser.getDbSetPropertyToEntityMap();
        AnalysisResult result = patternAnalyzer.analyzePatterns(entities, queryPatterns, schema, dbSetMapping);
        result.setQueryPatterns(queryPatterns); // Store query patterns in result

        // Step 5: Apply filters
        result.setDenormalizationCandidates(
                filterCandidates(result.getDenormalizationCandidates())
        );

        log.info("Analysis complete. Identified {} denormalization candidates after filtering",
                result.getDenormalizationCandidates().size());

        return result;
    }

    public List<NoSQLRecommendation> generateRecommendations(AnalysisResult result) {
        return generateRecommendations(result, null, null, null);
    }
    
    public List<NoSQLRecommendation> generateRecommendations(AnalysisResult result, 
                                                            DatabaseSchema schema,
                                                            List<QueryPattern> queryPatterns,
                                                            Map<String, Object> productionMetrics) {
        log.info("Generating AI-powered recommendations for {} candidates",
                result.getDenormalizationCandidates().size());
        
        if (productionMetrics != null && !productionMetrics.isEmpty()) {
            log.info("Including production metrics from Query Store in recommendation generation");
        }

        // Use stored schema if schema parameter is null
        DatabaseSchema schemaToUse = (schema != null) ? schema : this.currentSchema;
        List<QueryPattern> patternsToUse = (queryPatterns != null) ? queryPatterns : result.getQueryPatterns();
        
        List<NoSQLRecommendation> recommendations = recommendationEngine.generateRecommendations(
            result, schemaToUse, patternsToUse, productionMetrics);

        // Apply target service filter
        if (!config.getTargetServices().isEmpty() &&
                config.getTargetServices().size() < 3) {
            recommendations = recommendations.stream()
                    .filter(rec -> config.getTargetServices().contains(rec.getTargetService()))
                    .collect(Collectors.toList());
        }

        log.info("Generated {} recommendations", recommendations.size());

        return recommendations;
    }

    private List<DenormalizationCandidate> filterCandidates(List<DenormalizationCandidate> candidates) {
        if (config.getComplexityFilter() == ComplexityFilter.ALL) {
            return candidates;
        }

        return candidates.stream()
                .filter(candidate ->
                        candidate.getComplexity().toString().equalsIgnoreCase(
                                config.getComplexityFilter().toString()))
                .collect(Collectors.toList());
    }
}