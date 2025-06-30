package org.carball.aden.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class ConfigurationLoader {
    
    private static final String DEFAULT_CONFIG_FILE = "aden-config.json";
    private static final String USER_CONFIG_DIR = ".aden";
    private static final String USER_CONFIG_FILE = "config.json";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Loads configuration using the hierarchy: CLI args > env vars > project config > user config > defaults
     */
    public MigrationThresholds loadConfiguration(String[] args, String projectDirectory) {
        log.debug("Loading configuration with project directory: {}", projectDirectory);
        
        // Start with defaults
        MigrationThresholds.MigrationThresholdsBuilder builder = MigrationThresholds.builder();
        
        // 1. Load user config file if it exists
        loadUserConfig(builder);
        
        // 2. Load project-specific config file if it exists
        loadProjectConfig(builder, projectDirectory);
        
        // 3. Apply environment variables
        applyEnvironmentVariables(builder);
        
        // 4. Apply CLI arguments (highest priority)
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
    public MigrationThresholds loadConfigurationWithProfile(String profileName, String[] args, String projectDirectory) {
        // Start with profile
        MigrationThresholds profileThresholds = loadProfile(profileName);
        MigrationThresholds.MigrationThresholdsBuilder builder = profileThresholds.toBuilder();
        
        // Apply overrides in hierarchy order
        loadUserConfig(builder);
        loadProjectConfig(builder, projectDirectory);
        applyEnvironmentVariables(builder);
        applyCLIArguments(builder, args);
        
        MigrationThresholds thresholds = builder.build();
        thresholds.validate();
        
        log.info("Configuration loaded with profile '{}': {}", profileName, thresholds.getConfigurationSummary());
        return thresholds;
    }
    
    private void loadUserConfig(MigrationThresholds.MigrationThresholdsBuilder builder) {
        String userHome = System.getProperty("user.home");
        if (userHome == null) return;
        
        Path userConfigPath = Paths.get(userHome, USER_CONFIG_DIR, USER_CONFIG_FILE);
        if (Files.exists(userConfigPath)) {
            try {
                log.debug("Loading user config from: {}", userConfigPath);
                JsonNode config = objectMapper.readTree(userConfigPath.toFile());
                applyJsonConfig(builder, config);
            } catch (IOException e) {
                log.warn("Failed to load user config from {}: {}", userConfigPath, e.getMessage());
            }
        }
    }
    
    private void loadProjectConfig(MigrationThresholds.MigrationThresholdsBuilder builder, String projectDirectory) {
        if (projectDirectory == null) return;
        
        Path projectConfigPath = Paths.get(projectDirectory, DEFAULT_CONFIG_FILE);
        if (Files.exists(projectConfigPath)) {
            try {
                log.debug("Loading project config from: {}", projectConfigPath);
                JsonNode config = objectMapper.readTree(projectConfigPath.toFile());
                applyJsonConfig(builder, config);
            } catch (IOException e) {
                log.warn("Failed to load project config from {}: {}", projectConfigPath, e.getMessage());
            }
        }
    }
    
    private void applyJsonConfig(MigrationThresholds.MigrationThresholdsBuilder builder, JsonNode config) {
        if (config.has("highFrequencyThreshold")) {
            builder.highFrequencyThreshold(config.get("highFrequencyThreshold").asInt());
        }
        if (config.has("mediumFrequencyThreshold")) {
            builder.mediumFrequencyThreshold(config.get("mediumFrequencyThreshold").asInt());
        }
        if (config.has("highReadWriteRatio")) {
            builder.highReadWriteRatio(config.get("highReadWriteRatio").asDouble());
        }
        if (config.has("complexRelationshipPenalty")) {
            builder.complexRelationshipPenalty(config.get("complexRelationshipPenalty").asInt());
        }
        if (config.has("complexityPenaltyMultiplier")) {
            builder.complexityPenaltyMultiplier(config.get("complexityPenaltyMultiplier").asDouble());
        }
        if (config.has("minimumMigrationScore")) {
            builder.minimumMigrationScore(config.get("minimumMigrationScore").asInt());
        }
        if (config.has("excellentCandidateThreshold")) {
            builder.excellentCandidateThreshold(config.get("excellentCandidateThreshold").asInt());
        }
        if (config.has("strongCandidateThreshold")) {
            builder.strongCandidateThreshold(config.get("strongCandidateThreshold").asInt());
        }
        if (config.has("goodCandidateThreshold")) {
            builder.goodCandidateThreshold(config.get("goodCandidateThreshold").asInt());
        }
        if (config.has("fairCandidateThreshold")) {
            builder.fairCandidateThreshold(config.get("fairCandidateThreshold").asInt());
        }
        if (config.has("complexQueryRequirement")) {
            builder.complexQueryRequirement(config.get("complexQueryRequirement").asInt());
        }
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
     * Generates a sample configuration file in the specified directory.
     */
    public void generateConfigFile(String directory, MigrationProfile profile) throws IOException {
        MigrationThresholds thresholds = profile != null ? profile.buildThresholds() : MigrationThresholds.defaults();
        
        Path configPath = Paths.get(directory, DEFAULT_CONFIG_FILE);
        
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"_comment\": \"").append(thresholds.getProfileDescription()).append("\",\n");
        json.append("  \"highFrequencyThreshold\": ").append(thresholds.getHighFrequencyThreshold()).append(",\n");
        json.append("  \"mediumFrequencyThreshold\": ").append(thresholds.getMediumFrequencyThreshold()).append(",\n");
        json.append("  \"highReadWriteRatio\": ").append(thresholds.getHighReadWriteRatio()).append(",\n");
        json.append("  \"complexRelationshipPenalty\": ").append(thresholds.getComplexRelationshipPenalty()).append(",\n");
        json.append("  \"complexityPenaltyMultiplier\": ").append(thresholds.getComplexityPenaltyMultiplier()).append(",\n");
        json.append("  \"minimumMigrationScore\": ").append(thresholds.getMinimumMigrationScore()).append(",\n");
        json.append("  \"excellentCandidateThreshold\": ").append(thresholds.getExcellentCandidateThreshold()).append(",\n");
        json.append("  \"strongCandidateThreshold\": ").append(thresholds.getStrongCandidateThreshold()).append(",\n");
        json.append("  \"goodCandidateThreshold\": ").append(thresholds.getGoodCandidateThreshold()).append(",\n");
        json.append("  \"fairCandidateThreshold\": ").append(thresholds.getFairCandidateThreshold()).append(",\n");
        json.append("  \"complexQueryRequirement\": ").append(thresholds.getComplexQueryRequirement()).append("\n");
        json.append("}\n");
        
        Files.write(configPath, json.toString().getBytes());
        log.info("Generated configuration file: {}", configPath);
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
            
            Configuration Files:
              ~/.aden/config.json                   User-specific configuration
              ./aden-config.json                    Project-specific configuration
            
            Priority Order (highest to lowest):
              1. CLI arguments
              2. Environment variables  
              3. Project configuration file
              4. User configuration file
              5. Profile defaults or built-in defaults
            """;
    }
}