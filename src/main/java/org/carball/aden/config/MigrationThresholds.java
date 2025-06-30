package org.carball.aden.config;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder(toBuilder = true)
@Slf4j
public class MigrationThresholds {
    
    // Frequency thresholds
    @Builder.Default
    private int highFrequencyThreshold = 50;
    
    @Builder.Default
    private int mediumFrequencyThreshold = 20;
    
    // Complexity scoring
    @Builder.Default
    private int complexRelationshipPenalty = 10;
    
    @Builder.Default
    private double complexityPenaltyMultiplier = 1.0;
    
    // Read/write patterns
    @Builder.Default
    private double highReadWriteRatio = 10.0;
    
    // Migration scoring thresholds
    @Builder.Default
    private int minimumMigrationScore = 30;
    
    @Builder.Default
    private int excellentCandidateThreshold = 150;
    
    @Builder.Default
    private int strongCandidateThreshold = 100;
    
    @Builder.Default
    private int goodCandidateThreshold = 60;
    
    @Builder.Default
    private int fairCandidateThreshold = 30;
    
    // Complex query requirements
    @Builder.Default
    private int complexQueryRequirement = 5;
    
    // Profile information
    @Builder.Default
    private String profileName = "default";
    
    @Builder.Default
    private String profileDescription = "Default balanced thresholds";
    
    /**
     * Creates default thresholds suitable for most applications.
     */
    public static MigrationThresholds defaults() {
        return MigrationThresholds.builder()
                .profileName("default")
                .profileDescription("Default balanced thresholds")
                .build();
    }
    
    /**
     * Validates the threshold configuration and logs warnings for potentially problematic values.
     */
    public void validate() {
        if (highFrequencyThreshold <= mediumFrequencyThreshold) {
            log.warn("High frequency threshold ({}) should be greater than medium frequency threshold ({})",
                    highFrequencyThreshold, mediumFrequencyThreshold);
        }
        
        if (mediumFrequencyThreshold <= 0) {
            log.warn("Medium frequency threshold ({}) should be positive", mediumFrequencyThreshold);
        }
        
        if (highReadWriteRatio <= 1.0) {
            log.warn("High read/write ratio ({}) should be greater than 1.0", highReadWriteRatio);
        }
        
        if (excellentCandidateThreshold <= strongCandidateThreshold) {
            log.warn("Excellent candidate threshold ({}) should be greater than strong candidate threshold ({})",
                    excellentCandidateThreshold, strongCandidateThreshold);
        }
        
        if (strongCandidateThreshold <= goodCandidateThreshold) {
            log.warn("Strong candidate threshold ({}) should be greater than good candidate threshold ({})",
                    strongCandidateThreshold, goodCandidateThreshold);
        }
        
        if (goodCandidateThreshold <= fairCandidateThreshold) {
            log.warn("Good candidate threshold ({}) should be greater than fair candidate threshold ({})",
                    goodCandidateThreshold, fairCandidateThreshold);
        }
        
        if (complexityPenaltyMultiplier < 0) {
            log.warn("Complexity penalty multiplier ({}) should not be negative", complexityPenaltyMultiplier);
        }
        
        log.debug("Using thresholds - High: {}, Medium: {}, ReadWrite: {}, Profile: {}",
                highFrequencyThreshold, mediumFrequencyThreshold, highReadWriteRatio, profileName);
    }
    
    /**
     * Suggests configuration adjustments based on application characteristics.
     */
    public void suggestAdjustments(int entityCount, int totalQueryPatterns, int maxFrequency) {
        log.info("Application characteristics: {} entities, {} query patterns, max frequency: {}",
                entityCount, totalQueryPatterns, maxFrequency);
        
        if (maxFrequency < mediumFrequencyThreshold) {
            log.warn("Maximum query frequency ({}) is below medium threshold ({}). " +
                    "Consider using --profile=aggressive or lowering thresholds", 
                    maxFrequency, mediumFrequencyThreshold);
        }
        
        if (entityCount < 5 && highFrequencyThreshold > 20) {
            log.info("Small application detected ({} entities). Consider --profile=startup-aggressive for better results",
                    entityCount);
        }
        
        if (entityCount > 50 && highFrequencyThreshold < 100) {
            log.info("Large application detected ({} entities). Consider --profile=enterprise-conservative to focus on highest-impact migrations",
                    entityCount);
        }
        
        if (totalQueryPatterns == 0) {
            log.warn("No query patterns detected. Ensure your application contains LINQ queries with Include() statements");
        }
    }
    
    /**
     * Returns a description of the current configuration for user feedback.
     */
    public String getConfigurationSummary() {
        return String.format("Profile: %s | High freq: %d | Medium freq: %d | Read/Write: %.1f | Min score: %d",
                profileName, highFrequencyThreshold, mediumFrequencyThreshold, 
                highReadWriteRatio, minimumMigrationScore);
    }
}