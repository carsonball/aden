package org.carball.aden.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigurationLoaderTest {

    private ConfigurationLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ConfigurationLoader();
    }

    @Test
    void shouldLoadDefaultConfiguration() {
        // When
        MigrationThresholds thresholds = loader.loadConfiguration(new String[0]);
        
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
    void shouldApplyProfileWithOverrides() {
        // Given
        String[] args = {
                "--thresholds.high-frequency", "25"
        };

        // When
        MigrationThresholds thresholds = loader.loadConfigurationWithProfile(
                "discovery", args);

        // Then - CLI > env vars > profile > defaults
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(25); // CLI override
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(4); // From discovery profile (20 * 0.2)
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
        MigrationThresholds thresholds = loader.loadConfiguration(args);

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
        MigrationThresholds thresholds = loader.loadConfiguration(args);

        // Then - Invalid values should be ignored, valid ones applied
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(50); // Default (invalid ignored)
        assertThat(thresholds.getHighReadWriteRatio()).isEqualTo(10.0); // Default (invalid ignored)
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(15); // Valid value applied
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
        assertThat(help).contains("Priority Order");
    }
}