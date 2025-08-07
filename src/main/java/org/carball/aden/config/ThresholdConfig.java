package org.carball.aden.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ThresholdConfig {
    
    // Query pattern analysis thresholds
    @JsonProperty("always_loaded_together_frequency_threshold")
    private int alwaysLoadedTogetherFrequencyThreshold = 5;
    
    @JsonProperty("total_access_frequency_threshold") 
    private int totalAccessFrequencyThreshold = 8;
    
    @JsonProperty("medium_frequency_threshold")
    private int mediumFrequencyThreshold = 2;

    @JsonProperty("complex_eager_loading_frequency_threshold")
    private int complexEagerLoadingFrequencyThreshold = 1;
    
    // Production metrics thresholds
    @JsonProperty("high_production_usage_threshold")
    private int highProductionUsageThreshold = 100;
    
    @JsonProperty("production_co_access_threshold")
    private int productionCoAccessThreshold = 50;
    
    @JsonProperty("related_entity_co_access_threshold") 
    private int relatedEntityCoAccessThreshold = 15;
    
    @JsonProperty("co_access_scoring_threshold")
    private int coAccessScoringThreshold = 50;
    
    // Read/write ratio thresholds
    @JsonProperty("read_heavy_ratio_threshold")
    private double readHeavyRatioThreshold = 3.0;
    
    @JsonProperty("medium_read_ratio_threshold")
    private double mediumReadRatioThreshold = 2.5;
    
    // Entity relationship thresholds
    @JsonProperty("min_read_count_for_ratio_analysis")
    private int minReadCountForRatioAnalysis = 2;
    
    /**
     * Create discovery-appropriate default thresholds.
     * These values are tuned to be sensitive enough for discovery scenarios
     * while avoiding false positives.
     */
    public static ThresholdConfig createDiscoveryDefaults() {
        return new ThresholdConfig();
    }
    
    public String getDescription() {
        return String.format(
            "Thresholds: alwaysLoaded=%d, totalAccess=%d, mediumFreq=%d, complexQueries=%d, " +
            "prodUsage=%d, prodCoAccess=%d, relatedCoAccess=%d, readRatio=%.1f",
            alwaysLoadedTogetherFrequencyThreshold,
            totalAccessFrequencyThreshold,
            mediumFrequencyThreshold,
            complexEagerLoadingFrequencyThreshold,
            highProductionUsageThreshold,
            productionCoAccessThreshold,
            relatedEntityCoAccessThreshold,
            readHeavyRatioThreshold
        );
    }
}