package org.carball.aden.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.carball.aden.ai.JsonRecommendationEngine;
import org.carball.aden.config.DotNetAnalyzerConfig;
import org.carball.aden.config.ThresholdConfig;
import org.carball.aden.model.analysis.AnalysisResult;
import org.carball.aden.model.entity.EntityModel;
import org.carball.aden.model.query.QueryPattern;
import org.carball.aden.model.query.QueryStoreAnalysis;
import org.carball.aden.model.recommendation.NoSQLRecommendation;
import org.carball.aden.model.schema.DatabaseSchema;
import org.carball.aden.parser.EFModelParser;
import org.carball.aden.parser.LinqAnalyzer;
import org.carball.aden.parser.SchemaParser;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
public class DotNetAnalyzer {

    private final DotNetAnalyzerConfig config;
    private final EFModelParser efParser;
    private final LinqAnalyzer linqAnalyzer;
    private final DotNetPatternAnalyzer patternAnalyzer;
    private final JsonRecommendationEngine jsonRecommendationEngine;
    private DatabaseSchema currentSchema; // Store schema for recommendation generation

    public DotNetAnalyzer(DotNetAnalyzerConfig config) {
        this.config = config;
        
        // Get threshold configuration from config, use defaults if not provided
        ThresholdConfig thresholdConfig = config.getThresholdConfig() != null ?
                config.getThresholdConfig() : ThresholdConfig.createDiscoveryDefaults();

        this.efParser = new EFModelParser();
        this.linqAnalyzer = new LinqAnalyzer();
        this.patternAnalyzer = new DotNetPatternAnalyzer(thresholdConfig);
        this.jsonRecommendationEngine = new JsonRecommendationEngine(config.getOpenAiApiKey());

        log.info("Initialized DotNetAnalyzer with config: {}", config);
        log.info("Using thresholds: {}", thresholdConfig.getDescription());
    }

    public AnalysisResult analyze() throws IOException {
        return analyze(null);
    }
    
    public AnalysisResult analyze(QueryStoreAnalysis productionMetrics) throws IOException {
        log.info("Starting analysis of .NET Framework application");

        // Step 1: Parse database schema
        if (config.isVerbose()) {
            System.out.println("  - Parsing database schema...");
        }
        DatabaseSchema schema = SchemaParser.parseDDL(config.getSchemaFile());
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
        AnalysisResult result = patternAnalyzer.analyzePatterns(entities, queryPatterns, schema, dbSetMapping, productionMetrics);

        log.info("Analysis complete. Identified {} denormalization candidates",
                result.denormalizationCandidates().size());

        return result;
    }

    public List<NoSQLRecommendation> generateRecommendations(AnalysisResult result) {
        return generateRecommendations(result, null, null, null);
    }
    
    public List<NoSQLRecommendation> generateRecommendations(AnalysisResult result, 
                                                            DatabaseSchema schema,
                                                            List<QueryPattern> queryPatterns,
                                                            QueryStoreAnalysis productionMetrics) {
        log.info("Generating AI-powered recommendations for {} candidates",
                result.denormalizationCandidates().size());
        
        if (productionMetrics != null) {
            log.info("Including production metrics from Query Store in recommendation generation");
        }

        // Use stored schema if schema parameter is null
        DatabaseSchema schemaToUse = (schema != null) ? schema : this.currentSchema;
        List<QueryPattern> patternsToUse = (queryPatterns != null) ? queryPatterns : result.queryPatterns();
        
        List<NoSQLRecommendation> recommendations = jsonRecommendationEngine.generateRecommendations(
            result, schemaToUse, patternsToUse, productionMetrics);

        log.info("Generated {} recommendations", recommendations.size());

        return recommendations;
    }

}