package org.carball.aden.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigurationLoaderTest {

    private ConfigurationLoader loader;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        loader = new ConfigurationLoader();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldLoadDefaultConfiguration() {
        // When
        MigrationThresholds thresholds = loader.loadConfiguration(new String[0], null);
        
        // Then
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(50);
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(20);
        assertThat(thresholds.getProfileName()).isEqualTo("default");
    }

    @Test
    void shouldLoadSpecificProfile() {
        // When
        MigrationThresholds thresholds = loader.loadProfile("startup-aggressive");
        
        // Then
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(15);
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(6);
        assertThat(thresholds.getProfileName()).isEqualTo("startup-aggressive");
    }

    @Test
    void shouldThrowExceptionForUnknownProfile() {
        // When/Then
        assertThatThrownBy(() -> loader.loadProfile("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown migration profile: nonexistent");
    }

    @Test
    void shouldApplyConfigurationHierarchy() throws IOException {
        // Given - Create project config file
        Path projectConfig = tempDir.resolve("aden-config.json");
        String configJson = """
            {
                "highFrequencyThreshold": 75,
                "mediumFrequencyThreshold": 25,
                "highReadWriteRatio": 8.0
            }
            """;
        Files.writeString(projectConfig, configJson);

        // Environment variable simulation (we'll test CLI override)
        String[] args = {
                "--thresholds.high-frequency", "100",
                "--thresholds.read-write-ratio", "12.0"
        };

        // When
        MigrationThresholds thresholds = loader.loadConfiguration(args, tempDir.toString());

        // Then - CLI should override project config, project config should override defaults
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(100); // CLI override
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(25); // From project config
        assertThat(thresholds.getHighReadWriteRatio()).isEqualTo(12.0); // CLI override
        assertThat(thresholds.getComplexRelationshipPenalty()).isEqualTo(10); // Default value
    }

    @Test
    void shouldApplyProfileWithOverrides() throws IOException {
        // Given - Create project config file
        Path projectConfig = tempDir.resolve("aden-config.json");
        String configJson = """
            {
                "mediumFrequencyThreshold": 8
            }
            """;
        Files.writeString(projectConfig, configJson);

        String[] args = {
                "--thresholds.high-frequency", "25"
        };

        // When
        MigrationThresholds thresholds = loader.loadConfigurationWithProfile(
                "discovery", args, tempDir.toString());

        // Then - CLI > project config > profile > defaults
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(25); // CLI override
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(8); // Project config override
        assertThat(thresholds.getHighReadWriteRatio()).isEqualTo(5.0); // From discovery profile
        assertThat(thresholds.getProfileName()).isEqualTo("discovery");
    }

    @Test
    void shouldParseCLIArguments() {
        // Given
        String[] args = {
                "--thresholds.high-frequency", "60",
                "--thresholds.medium-frequency", "30",
                "--thresholds.read-write-ratio", "15.5",
                "--thresholds.complex-penalty", "5",
                "--thresholds.complexity-multiplier", "1.5",
                "--thresholds.min-score", "50",
                "--thresholds.excellent", "180",
                "--thresholds.strong", "120",
                "--thresholds.good", "80",
                "--thresholds.fair", "40",
                "--thresholds.complex-queries", "3"
        };

        // When
        MigrationThresholds thresholds = loader.loadConfiguration(args, null);

        // Then
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(60);
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(30);
        assertThat(thresholds.getHighReadWriteRatio()).isEqualTo(15.5);
        assertThat(thresholds.getComplexRelationshipPenalty()).isEqualTo(5);
        assertThat(thresholds.getComplexityPenaltyMultiplier()).isEqualTo(1.5);
        assertThat(thresholds.getMinimumMigrationScore()).isEqualTo(50);
        assertThat(thresholds.getExcellentCandidateThreshold()).isEqualTo(180);
        assertThat(thresholds.getStrongCandidateThreshold()).isEqualTo(120);
        assertThat(thresholds.getGoodCandidateThreshold()).isEqualTo(80);
        assertThat(thresholds.getFairCandidateThreshold()).isEqualTo(40);
        assertThat(thresholds.getComplexQueryRequirement()).isEqualTo(3);
    }

    @Test
    void shouldIgnoreInvalidCLIValues() {
        // Given
        String[] args = {
                "--thresholds.high-frequency", "not-a-number",
                "--thresholds.read-write-ratio", "invalid",
                "--thresholds.medium-frequency", "15" // This should still work
        };

        // When
        MigrationThresholds thresholds = loader.loadConfiguration(args, null);

        // Then - Invalid values should be ignored, valid ones applied
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(50); // Default (invalid ignored)
        assertThat(thresholds.getHighReadWriteRatio()).isEqualTo(10.0); // Default (invalid ignored)
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(15); // Valid value applied
    }

    @Test
    void shouldLoadProjectConfigFromFile() throws IOException {
        // Given
        Path projectConfig = tempDir.resolve("aden-config.json");
        String configJson = """
            {
                "highFrequencyThreshold": 35,
                "mediumFrequencyThreshold": 15,
                "highReadWriteRatio": 7.5,
                "complexRelationshipPenalty": 8,
                "complexityPenaltyMultiplier": 1.2,
                "minimumMigrationScore": 25,
                "excellentCandidateThreshold": 140,
                "strongCandidateThreshold": 90,
                "goodCandidateThreshold": 55,
                "fairCandidateThreshold": 25,
                "complexQueryRequirement": 4
            }
            """;
        Files.writeString(projectConfig, configJson);

        // When
        MigrationThresholds thresholds = loader.loadConfiguration(new String[0], tempDir.toString());

        // Then
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(35);
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(15);
        assertThat(thresholds.getHighReadWriteRatio()).isEqualTo(7.5);
        assertThat(thresholds.getComplexRelationshipPenalty()).isEqualTo(8);
        assertThat(thresholds.getComplexityPenaltyMultiplier()).isEqualTo(1.2);
        assertThat(thresholds.getMinimumMigrationScore()).isEqualTo(25);
        assertThat(thresholds.getExcellentCandidateThreshold()).isEqualTo(140);
        assertThat(thresholds.getStrongCandidateThreshold()).isEqualTo(90);
        assertThat(thresholds.getGoodCandidateThreshold()).isEqualTo(55);
        assertThat(thresholds.getFairCandidateThreshold()).isEqualTo(25);
        assertThat(thresholds.getComplexQueryRequirement()).isEqualTo(4);
    }

    @Test
    void shouldGenerateValidConfigFile() throws IOException {
        // When
        loader.generateConfigFile(tempDir.toString(), MigrationProfile.RETAIL);

        // Then
        Path configFile = tempDir.resolve("aden-config.json");
        assertThat(configFile).exists();

        String content = Files.readString(configFile);
        assertThat(content).contains("\"highFrequencyThreshold\": 35"); // 50 * 0.7
        assertThat(content).contains("\"mediumFrequencyThreshold\": 14"); // 20 * 0.7
        assertThat(content).contains("\"highReadWriteRatio\": 5.0"); // Retail-specific override
        assertThat(content).contains("\"complexQueryRequirement\": 3"); // Retail-specific override

        // Verify it's valid JSON
        Map<String, Object> parsed = objectMapper.readValue(content, Map.class);
        assertThat(parsed).containsKey("highFrequencyThreshold");
        assertThat(parsed).containsKey("_comment");
    }

    @Test
    void shouldGenerateDefaultConfigFile() throws IOException {
        // When
        loader.generateConfigFile(tempDir.toString(), null);

        // Then
        Path configFile = tempDir.resolve("aden-config.json");
        assertThat(configFile).exists();

        String content = Files.readString(configFile);
        assertThat(content).contains("\"highFrequencyThreshold\": 50");
        assertThat(content).contains("\"mediumFrequencyThreshold\": 20");
        assertThat(content).contains("\"highReadWriteRatio\": 10.0");

        // Verify it's valid JSON
        Map<String, Object> parsed = objectMapper.readValue(content, Map.class);
        assertThat(parsed).containsKey("highFrequencyThreshold");
    }

    @Test
    void shouldHandleMissingConfigFiles() {
        // Given - No config files exist
        Path nonExistentDir = tempDir.resolve("nonexistent");

        // When
        MigrationThresholds thresholds = loader.loadConfiguration(new String[0], nonExistentDir.toString());

        // Then - Should use defaults without error
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(50);
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(20);
    }

    @Test
    void shouldHandleInvalidJsonConfigFile() throws IOException {
        // Given - Invalid JSON config file
        Path projectConfig = tempDir.resolve("aden-config.json");
        Files.writeString(projectConfig, "{ invalid json }");

        // When
        MigrationThresholds thresholds = loader.loadConfiguration(new String[0], tempDir.toString());

        // Then - Should use defaults and not crash
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(50);
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(20);
    }

    @Test
    void shouldProvideThresholdHelp() {
        // When
        String help = ConfigurationLoader.getThresholdHelp();

        // Then
        assertThat(help).contains("Threshold Configuration Options:");
        assertThat(help).contains("CLI Arguments:");
        assertThat(help).contains("--thresholds.high-frequency");
        assertThat(help).contains("Environment Variables:");
        assertThat(help).contains("ADEN_HIGH_FREQUENCY_THRESHOLD");
        assertThat(help).contains("Configuration Files:");
        assertThat(help).contains("~/.aden/config.json");
        assertThat(help).contains("Priority Order");
    }
}