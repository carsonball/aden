package org.carball.aden.config;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class ConfigurationLoader {
    
    /**
     * Loads configuration using the hierarchy: CLI args > env vars > defaults
     */
    public MigrationThresholds loadConfiguration(String[] args) {
        log.debug("Loading configuration");
        
        // Start with defaults
        MigrationThresholds.MigrationThresholdsBuilder builder = MigrationThresholds.builder();
        
        // 1. Apply environment variables
        applyEnvironmentVariables(builder);
        
        // 2. Apply CLI arguments (highest priority)
        applyCLIArguments(builder, args);
        
        MigrationThresholds thresholds = builder.build();
        thresholds.validate();
        
        log.info("Configuration loaded: {}", thresholds.getConfigurationSummary());
        return thresholds;
    }
    
    /**
     * Loads configuration from a specific profile.
     */
    public MigrationThresholds loadProfile(String profileName) {
        try {
            MigrationProfile profile = MigrationProfile.fromName(profileName);
            MigrationThresholds thresholds = profile.buildThresholds();
            log.info("Loaded profile '{}': {}", profileName, thresholds.getConfigurationSummary());
            return thresholds;
        } catch (IllegalArgumentException e) {
            log.error("Unknown profile: {}. {}", profileName, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Loads and applies profile, then overlays with other configuration sources.
     */
    public MigrationThresholds loadConfigurationWithProfile(String profileName, String[] args) {
        // Start with profile
        MigrationThresholds profileThresholds = loadProfile(profileName);
        MigrationThresholds.MigrationThresholdsBuilder builder = profileThresholds.toBuilder();
        
        // Apply overrides in hierarchy order
        applyEnvironmentVariables(builder);
        applyCLIArguments(builder, args);
        
        MigrationThresholds thresholds = builder.build();
        thresholds.validate();
        
        log.info("Configuration loaded with profile '{}': {}", profileName, thresholds.getConfigurationSummary());
        return thresholds;
    }
    
    
    private void applyEnvironmentVariables(MigrationThresholds.MigrationThresholdsBuilder builder) {
        Map<String, String> env = System.getenv();
        
        if (env.containsKey("ADEN_HIGH_FREQUENCY_THRESHOLD")) {
            builder.highFrequencyThreshold(Integer.parseInt(env.get("ADEN_HIGH_FREQUENCY_THRESHOLD")));
        }
        if (env.containsKey("ADEN_MEDIUM_FREQUENCY_THRESHOLD")) {
            builder.mediumFrequencyThreshold(Integer.parseInt(env.get("ADEN_MEDIUM_FREQUENCY_THRESHOLD")));
        }
        if (env.containsKey("ADEN_HIGH_READ_WRITE_RATIO")) {
            builder.highReadWriteRatio(Double.parseDouble(env.get("ADEN_HIGH_READ_WRITE_RATIO")));
        }
        if (env.containsKey("ADEN_COMPLEX_RELATIONSHIP_PENALTY")) {
            builder.complexRelationshipPenalty(Integer.parseInt(env.get("ADEN_COMPLEX_RELATIONSHIP_PENALTY")));
        }
        if (env.containsKey("ADEN_COMPLEXITY_PENALTY_MULTIPLIER")) {
            builder.complexityPenaltyMultiplier(Double.parseDouble(env.get("ADEN_COMPLEXITY_PENALTY_MULTIPLIER")));
        }
        if (env.containsKey("ADEN_MINIMUM_MIGRATION_SCORE")) {
            builder.minimumMigrationScore(Integer.parseInt(env.get("ADEN_MINIMUM_MIGRATION_SCORE")));
        }
    }
    
    private void applyCLIArguments(MigrationThresholds.MigrationThresholdsBuilder builder, String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            String arg = args[i];
            String value = args[i + 1];
            
            try {
                switch (arg) {
                    case "--thresholds.high-frequency":
                        builder.highFrequencyThreshold(Integer.parseInt(value));
                        break;
                    case "--thresholds.medium-frequency":
                        builder.mediumFrequencyThreshold(Integer.parseInt(value));
                        break;
                    case "--thresholds.read-write-ratio":
                        builder.highReadWriteRatio(Double.parseDouble(value));
                        break;
                    case "--thresholds.complex-penalty":
                        builder.complexRelationshipPenalty(Integer.parseInt(value));
                        break;
                    case "--thresholds.complexity-multiplier":
                        builder.complexityPenaltyMultiplier(Double.parseDouble(value));
                        break;
                    case "--thresholds.min-score":
                        builder.minimumMigrationScore(Integer.parseInt(value));
                        break;
                    case "--thresholds.excellent":
                        builder.excellentCandidateThreshold(Integer.parseInt(value));
                        break;
                    case "--thresholds.strong":
                        builder.strongCandidateThreshold(Integer.parseInt(value));
                        break;
                    case "--thresholds.good":
                        builder.goodCandidateThreshold(Integer.parseInt(value));
                        break;
                    case "--thresholds.fair":
                        builder.fairCandidateThreshold(Integer.parseInt(value));
                        break;
                    case "--thresholds.complex-queries":
                        builder.complexQueryRequirement(Integer.parseInt(value));
                        break;
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid numeric value for {}: {}", arg, value);
            }
        }
    }
    
    
    /**
     * Returns help text for threshold configuration options.
     */
    public static String getThresholdHelp() {
        return """
            Threshold Configuration Options:
            
            CLI Arguments:
              --thresholds.high-frequency <num>     High frequency threshold for eager loading
              --thresholds.medium-frequency <num>   Medium frequency threshold
              --thresholds.read-write-ratio <num>   Minimum read/write ratio for optimization
              --thresholds.complex-penalty <num>    Penalty points for complex relationships
              --thresholds.complexity-multiplier <num> Complexity penalty multiplier
              --thresholds.min-score <num>          Minimum score for migration candidates
              --thresholds.excellent <num>          Threshold for 'excellent' candidates
              --thresholds.strong <num>             Threshold for 'strong' candidates
              --thresholds.good <num>               Threshold for 'good' candidates
              --thresholds.fair <num>               Threshold for 'fair' candidates
              --thresholds.complex-queries <num>    Required complex queries per entity
            
            Environment Variables:
              ADEN_HIGH_FREQUENCY_THRESHOLD         Same as --thresholds.high-frequency
              ADEN_MEDIUM_FREQUENCY_THRESHOLD       Same as --thresholds.medium-frequency
              ADEN_HIGH_READ_WRITE_RATIO           Same as --thresholds.read-write-ratio
              ADEN_COMPLEX_RELATIONSHIP_PENALTY    Same as --thresholds.complex-penalty
              ADEN_COMPLEXITY_PENALTY_MULTIPLIER   Same as --thresholds.complexity-multiplier
              ADEN_MINIMUM_MIGRATION_SCORE         Same as --thresholds.min-score
            
            Priority Order (highest to lowest):
              1. CLI arguments
              2. Environment variables
              3. Profile defaults or built-in defaults
            """;
    }
}