package org.carball.aden.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.carball.aden.model.analysis.*;
import org.carball.aden.model.recommendation.NoSQLRecommendation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class MigrationReport {

    private final AnalysisResult analysisResult;
    private final List<NoSQLRecommendation> recommendations;
    private final LocalDateTime timestamp;
    private final ObjectMapper objectMapper;

    public MigrationReport(AnalysisResult analysisResult, List<NoSQLRecommendation> recommendations) {
        this.analysisResult = analysisResult;
        this.recommendations = recommendations;
        this.timestamp = LocalDateTime.now();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public String toJson() {
        try {
            ReportData reportData = buildReportData();
            return objectMapper.writeValueAsString(reportData);
        } catch (Exception e) {
            log.error("Error generating JSON report", e);
            throw new RuntimeException("Failed to generate JSON report", e);
        }
    }

    public String toMarkdown() {
        StringBuilder md = new StringBuilder();

        // Header
        md.append("# .NET Framework to AWS NoSQL Migration Analysis Report\n\n");
        md.append("**Generated:** ").append(timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("  \n");
        md.append("**Analyzer Version:** 1.0.0  \n\n");

        // Executive Summary
        md.append("## Executive Summary\n\n");
        md.append(generateExecutiveSummary()).append("\n\n");

        // Analysis Overview
        md.append("## Analysis Overview\n\n");
        md.append("| Metric | Value |\n");
        md.append("|--------|-------|\n");
        md.append("| Entities Analyzed | ").append(analysisResult.getUsageProfiles().size()).append(" |\n");
        md.append("| Query Patterns Found | ").append(analysisResult.getQueryPatterns().size()).append(" |\n");
        md.append("| Denormalization Candidates | ").append(analysisResult.getDenormalizationCandidates().size()).append(" |\n");
        md.append("| Recommendations Generated | ").append(recommendations.size()).append(" |\n\n");

        // Scoring Guide
        md.append("### Migration Score Guide\n\n");
        md.append("| Score Range | Priority | Description |\n");
        md.append("|-------------|----------|-------------|\n");
        md.append("| 150+ | 🔴 **Immediate** | Excellent candidate - migrate immediately |\n");
        md.append("| 100-149 | 🟠 **High** | Strong candidate - high priority |\n");
        md.append("| 60-99 | 🟡 **Medium** | Good candidate - medium priority |\n");
        md.append("| 30-59 | 🟢 **Low** | Fair candidate - low priority |\n");
        md.append("| 0-29 | ⚪ **Reconsider** | Poor candidate - reconsider approach |\n\n");

        // Migration Candidates
        md.append("## Migration Candidates\n\n");
        
        if (analysisResult.getDenormalizationCandidates().isEmpty()) {
            md.append("**No entities met the criteria for NoSQL migration recommendations.**\n\n");
        }
        
        for (DenormalizationCandidate candidate : analysisResult.getDenormalizationCandidates()) {
            md.append("### ").append(candidate.getPrimaryEntity()).append("\n\n");
            md.append("- **Complexity:** ").append(candidate.getComplexity()).append("\n");
            md.append("- **Score:** ").append(candidate.getScore())
                    .append(" (").append(candidate.getScoreInterpretation()).append(")\n");
            md.append("- **Reason:** ").append(candidate.getReason()).append("\n");
            md.append("- **Related Entities:** ").append(String.join(", ", candidate.getRelatedEntities())).append("\n");
            md.append("- **Recommended Target:** ").append(candidate.getRecommendedTarget().getDisplayName()).append("\n\n");
        }

        // Detailed Recommendations
        md.append("## Detailed Recommendations\n\n");
        int recNum = 1;
        for (NoSQLRecommendation rec : recommendations) {
            md.append("### ").append(recNum++).append(". ").append(rec.getPrimaryEntity())
                    .append(" → ").append(rec.getTargetService().getDisplayName()).append("\n\n");

            md.append("#### Migration Details\n\n");
            md.append("- **Table/Collection Name:** `").append(rec.getTableName()).append("`\n\n");

            if (rec.getPartitionKey() != null) {
                md.append("#### Key Design\n\n");
                md.append("**Partition Key:**\n");
                md.append("- Attribute: `").append(rec.getPartitionKey().getAttribute()).append("`\n");
                md.append("- Type: ").append(rec.getPartitionKey().getType()).append("\n");
                if (!rec.getPartitionKey().getExamples().isEmpty()) {
                    md.append("- Examples: ").append(String.join(", ", rec.getPartitionKey().getExamples())).append("\n");
                }
                md.append("\n");

                if (rec.getSortKey() != null) {
                    md.append("**Sort Key:**\n");
                    md.append("- Attribute: `").append(rec.getSortKey().getAttribute()).append("`\n");
                    md.append("- Type: ").append(rec.getSortKey().getType()).append("\n");
                    if (!rec.getSortKey().getExamples().isEmpty()) {
                        md.append("- Examples: ").append(String.join(", ", rec.getSortKey().getExamples())).append("\n");
                    }
                    md.append("\n");
                }
            }

            if (!rec.getGlobalSecondaryIndexes().isEmpty()) {
                md.append("#### Global Secondary Indexes\n\n");
                rec.getGlobalSecondaryIndexes().forEach(gsi -> {
                    md.append("- **").append(gsi.getIndexName()).append("**\n");
                    md.append("  - Partition Key: `").append(gsi.getPartitionKey()).append("`\n");
                    if (gsi.getSortKey() != null) {
                        md.append("  - Sort Key: `").append(gsi.getSortKey()).append("`\n");
                    }
                    md.append("  - Purpose: ").append(gsi.getPurpose()).append("\n");
                });
                md.append("\n");
            }
            
            if (rec.getDesignRationale() != null) {
                md.append("#### Design Rationale\n\n");
                md.append("**Denormalization Strategy:** ").append(rec.getDesignRationale().getDenormalizationStrategy()).append("\n\n");
                md.append("**Key Design Justification:** ").append(rec.getDesignRationale().getKeyDesignJustification()).append("\n\n");
                md.append("**Relationship Handling:** ").append(rec.getDesignRationale().getRelationshipHandling()).append("\n\n");
                md.append("**Performance Optimizations:** ").append(rec.getDesignRationale().getPerformanceOptimizations()).append("\n\n");
                md.append("**Access Pattern Analysis:** ").append(rec.getDesignRationale().getAccessPatternAnalysis()).append("\n\n");
                
                if (rec.getDesignRationale().getTradeoffsConsidered() != null && !rec.getDesignRationale().getTradeoffsConsidered().isEmpty()) {
                    md.append("**Trade-offs Considered:**\n");
                    rec.getDesignRationale().getTradeoffsConsidered().forEach(tradeoff -> 
                        md.append("- ").append(tradeoff).append("\n"));
                    md.append("\n");
                }
            }
        }

        // Query Pattern Analysis
        md.append("## Query Pattern Analysis\n\n");
        Map<String, Long> patternCounts = analysisResult.getQueryPatterns().stream()
                .collect(Collectors.groupingBy(p -> p.getQueryType().toString(), Collectors.counting()));

        md.append("| Query Type | Count |\n");
        md.append("|------------|-------|\n");
        patternCounts.forEach((type, count) ->
                md.append("| ").append(type).append(" | ").append(count).append(" |\n"));
        md.append("\n");

        // Complexity Analysis
        if (analysisResult.getComplexityAnalysis() != null) {
            md.append("## Complexity Analysis\n\n");
            md.append("**Overall Complexity Score:** ").append(analysisResult.getComplexityAnalysis().getOverallComplexity()).append("\n\n");
            md.append("**Reason:** ").append(analysisResult.getComplexityAnalysis().getComplexityReason()).append("\n\n");

            md.append("### Entity Complexity Scores\n\n");
            md.append("| Entity | Complexity Score |\n");
            md.append("|--------|------------------|\n");
            analysisResult.getComplexityAnalysis().getEntityComplexityScores().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry ->
                            md.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" |\n"));
            md.append("\n");
        }

        // Next Steps
        md.append("## Next Steps\n\n");
        md.append("1. **Review Recommendations:** Validate the suggested NoSQL designs with your team\n");
        md.append("2. **Prioritize Migrations:** Start with low-complexity, high-value entities\n");
        md.append("3. **Proof of Concept:** Build a PoC for the highest-priority migration\n");
        md.append("4. **Performance Testing:** Validate query performance with realistic data volumes\n");
        md.append("5. **Migration Planning:** Create detailed migration scripts and rollback procedures\n\n");

        // Footer
        md.append("---\n\n");
        md.append("*Generated by .NET Framework to AWS NoSQL Migration Analyzer*\n");

        return md.toString();
    }

    private ReportData buildReportData() {
        ReportData report = new ReportData();

        // Metadata
        report.setAnalysisMetadata(new AnalysisMetadata(
                timestamp,
                "4.7.2", // Could be detected from project files
                "6.4.4", // Could be detected from packages
                analysisResult.getUsageProfiles().size(),
                analysisResult.getQueryPatterns().size()
        ));

        // Migration candidates with recommendations
        List<MigrationCandidate> migrationCandidates = analysisResult.getDenormalizationCandidates().stream()
                .map(candidate -> {
                    MigrationCandidate mc = new MigrationCandidate();
                    mc.setPrimaryEntity(candidate.getPrimaryEntity());
                    mc.setRelatedEntities(candidate.getRelatedEntities());
                    mc.setComplexity(candidate.getComplexity().toString());
                    mc.setScore(candidate.getScore());
                    mc.setReason(candidate.getReason());

                    // Find matching recommendation
                    recommendations.stream()
                            .filter(rec -> rec.getPrimaryEntity().equals(candidate.getPrimaryEntity()))
                            .findFirst()
                            .ifPresent(mc::setRecommendation);

                    // Add current pattern info
                    EntityUsageProfile profile = analysisResult.getUsageProfiles().get(candidate.getPrimaryEntity());
                    if (profile != null) {
                        CurrentPattern pattern = new CurrentPattern();
                        pattern.setEagerLoadingFrequency(profile.getEagerLoadingCount());
                        pattern.setReadWriteRatio(profile.getReadToWriteRatio());
                        pattern.setAverageIncludeDepth(calculateAverageIncludeDepth(profile));
                        mc.setCurrentPattern(pattern);
                    }

                    return mc;
                })
                .collect(Collectors.toList());

        report.setMigrationCandidates(migrationCandidates);

        // Risk assessment
        report.setRiskAssessment(generateRiskAssessment());

        return report;
    }

    private String generateExecutiveSummary() {
        int lowComplexityCount = (int) analysisResult.getDenormalizationCandidates().stream()
                .filter(c -> c.getComplexity() == MigrationComplexity.LOW)
                .count();

        return String.format(
                "Your .NET Framework application analysis identified **%d entities** suitable for NoSQL migration. " +
                        "The analysis found **%d high-priority** (low complexity) migrations. " +
                        "The most common pattern identified was eager loading of related entities, " +
                        "which maps well to NoSQL document structures.",
                analysisResult.getDenormalizationCandidates().size(),
                lowComplexityCount
        );
    }

    private int calculateAverageIncludeDepth(EntityUsageProfile profile) {
        // Simple heuristic based on query patterns
        long complexQueries = profile.getQueryPatterns().stream()
                .filter(p -> p.getType().equals("COMPLEX_EAGER_LOADING"))
                .count();

        if (complexQueries > 5) return 3;
        if (complexQueries > 0) return 2;
        return 1;
    }

    private RiskAssessment generateRiskAssessment() {
        RiskAssessment risk = new RiskAssessment();

        // High risk entities (complex relationships, circular references)
        List<String> highRiskEntities = analysisResult.getDenormalizationCandidates().stream()
                .filter(c -> c.getComplexity() == MigrationComplexity.HIGH)
                .map(DenormalizationCandidate::getPrimaryEntity)
                .collect(Collectors.toList());
        risk.setHighRiskEntities(highRiskEntities);

        // Potential data loss risks (many-to-many relationships)
        List<String> dataLossRisks = analysisResult.getUsageProfiles().values().stream()
                .filter(profile -> profile.getEntity().getNavigationProperties().stream()
                        .anyMatch(p -> p.getType() == org.carball.aden.model.entity.NavigationType.MANY_TO_MANY))
                .map(EntityUsageProfile::getEntityName)
                .collect(Collectors.toList());
        risk.setPotentialDataLoss(dataLossRisks);

        // Performance impacts
        List<PerformanceImpact> performanceImpacts = analysisResult.getDenormalizationCandidates().stream()
                .filter(c -> c.getRecommendedTarget() == NoSQLTarget.DOCUMENTDB)
                .map(c -> {
                    PerformanceImpact impact = new PerformanceImpact();
                    impact.setEntity(c.getPrimaryEntity());
                    impact.setIssue("Complex search queries may require additional indexing or ElasticSearch integration");
                    return impact;
                })
                .collect(Collectors.toList());
        risk.setPerformanceImpacts(performanceImpacts);

        return risk;
    }


    // Inner classes for JSON structure
    @lombok.Data
    private static class ReportData {
        private AnalysisMetadata analysisMetadata;
        private List<MigrationCandidate> migrationCandidates;
        private RiskAssessment riskAssessment;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class AnalysisMetadata {
        private LocalDateTime timestamp;
        private String dotnetFrameworkVersion;
        private String entityFrameworkVersion;
        private int totalEntitiesAnalyzed;
        private int totalQueryPatternsFound;
    }

    @lombok.Data
    private static class MigrationCandidate {
        private String primaryEntity;
        private List<String> relatedEntities;
        private String complexity;
        private int score;
        private String reason;
        private CurrentPattern currentPattern;
        private NoSQLRecommendation recommendation;
    }

    @lombok.Data
    private static class CurrentPattern {
        private int eagerLoadingFrequency;
        private double readWriteRatio;
        private int averageIncludeDepth;
    }

    @lombok.Data
    private static class RiskAssessment {
        private List<String> highRiskEntities;
        private List<String> potentialDataLoss;
        private List<PerformanceImpact> performanceImpacts;
    }

    @lombok.Data
    private static class PerformanceImpact {
        private String entity;
        private String issue;
    }
}