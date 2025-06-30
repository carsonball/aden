package org.carball.aden.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MigrationProfileTest {

    @Test
    void shouldCalculateStartupAggressiveThresholds() {
        // Given
        MigrationProfile profile = MigrationProfile.STARTUP_AGGRESSIVE;
        
        // When
        MigrationThresholds thresholds = profile.buildThresholds();
        
        // Then
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(15); // 50 * 0.3
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(6); // 20 * 0.3
        assertThat(thresholds.getHighReadWriteRatio()).isEqualTo(6.0); // 10.0 * 0.6
        assertThat(thresholds.getComplexityPenaltyMultiplier()).isEqualTo(1.8);
        assertThat(thresholds.getProfileName()).isEqualTo("startup-aggressive");
        assertThat(thresholds.getMinimumMigrationScore()).isEqualTo(15);
    }

    @Test
    void shouldCalculateEnterpriseConservativeThresholds() {
        // Given
        MigrationProfile profile = MigrationProfile.ENTERPRISE_CONSERVATIVE;
        
        // When
        MigrationThresholds thresholds = profile.buildThresholds();
        
        // Then
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(90); // 50 * 1.8
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(36); // 20 * 1.8
        assertThat(thresholds.getHighReadWriteRatio()).isEqualTo(15.0); // 10.0 * 1.5
        assertThat(thresholds.getExcellentCandidateThreshold()).isEqualTo(200);
        assertThat(thresholds.getStrongCandidateThreshold()).isEqualTo(150);
        assertThat(thresholds.getProfileName()).isEqualTo("enterprise-conservative");
    }

    @Test
    void shouldCalculateRetailSpecificThresholds() {
        // Given
        MigrationProfile profile = MigrationProfile.RETAIL;
        
        // When
        MigrationThresholds thresholds = profile.buildThresholds();
        
        // Then
        // Base calculations
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(35); // 50 * 0.7
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(14); // 20 * 0.7
        
        // Industry-specific overrides
        assertThat(thresholds.getHighReadWriteRatio()).isEqualTo(5.0); // Retail-specific override
        assertThat(thresholds.getComplexQueryRequirement()).isEqualTo(3); // Retail-specific override
        assertThat(thresholds.getProfileName()).isEqualTo("retail");
    }

    @Test
    void shouldCalculateHealthcareSpecificThresholds() {
        // Given
        MigrationProfile profile = MigrationProfile.HEALTHCARE;
        
        // When
        MigrationThresholds thresholds = profile.buildThresholds();
        
        // Then
        assertThat(thresholds.getComplexityPenaltyMultiplier()).isEqualTo(1.5); // Healthcare-specific override
        assertThat(thresholds.getMinimumMigrationScore()).isEqualTo(40); // Healthcare-specific override
        assertThat(thresholds.getProfileName()).isEqualTo("healthcare");
    }

    @Test
    void shouldCalculateManufacturingSpecificThresholds() {
        // Given
        MigrationProfile profile = MigrationProfile.MANUFACTURING;
        
        // When
        MigrationThresholds thresholds = profile.buildThresholds();
        
        // Then
        assertThat(thresholds.getComplexRelationshipPenalty()).isEqualTo(5); // Manufacturing-specific override
        assertThat(thresholds.getComplexQueryRequirement()).isEqualTo(4); // Manufacturing-specific override
        assertThat(thresholds.getProfileName()).isEqualTo("manufacturing");
    }

    @Test
    void shouldCalculateFinancialSpecificThresholds() {
        // Given
        MigrationProfile profile = MigrationProfile.FINANCIAL;
        
        // When
        MigrationThresholds thresholds = profile.buildThresholds();
        
        // Then
        assertThat(thresholds.getHighReadWriteRatio()).isEqualTo(15.0); // Financial-specific override
        assertThat(thresholds.getMinimumMigrationScore()).isEqualTo(60); // Financial-specific override
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(125); // 50 * 2.5
        assertThat(thresholds.getProfileName()).isEqualTo("financial");
    }

    @Test
    void shouldFindProfileByNameCaseInsensitive() {
        // When/Then
        assertThat(MigrationProfile.fromName("balanced")).isEqualTo(MigrationProfile.BALANCED);
        assertThat(MigrationProfile.fromName("BALANCED")).isEqualTo(MigrationProfile.BALANCED);
        assertThat(MigrationProfile.fromName("Balanced")).isEqualTo(MigrationProfile.BALANCED);
        assertThat(MigrationProfile.fromName("startup-aggressive")).isEqualTo(MigrationProfile.STARTUP_AGGRESSIVE);
        assertThat(MigrationProfile.fromName("STARTUP-AGGRESSIVE")).isEqualTo(MigrationProfile.STARTUP_AGGRESSIVE);
    }

    @Test
    void shouldThrowExceptionForUnknownProfile() {
        // When/Then
        assertThatThrownBy(() -> MigrationProfile.fromName("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown migration profile: nonexistent")
                .hasMessageContaining("Available profiles:");
    }

    @Test
    void shouldReturnAllAvailableProfiles() {
        // When
        String availableProfiles = MigrationProfile.getAvailableProfiles();
        
        // Then
        assertThat(availableProfiles).contains("conservative");
        assertThat(availableProfiles).contains("balanced");
        assertThat(availableProfiles).contains("aggressive");
        assertThat(availableProfiles).contains("discovery");
        assertThat(availableProfiles).contains("startup-aggressive");
        assertThat(availableProfiles).contains("enterprise-conservative");
        assertThat(availableProfiles).contains("retail");
        assertThat(availableProfiles).contains("healthcare");
        assertThat(availableProfiles).contains("manufacturing");
        assertThat(availableProfiles).contains("financial");
    }

    @Test
    void shouldProvideProfileHelp() {
        // When
        String help = MigrationProfile.getProfileHelp();
        
        // Then
        assertThat(help).contains("Available Migration Profiles:");
        assertThat(help).contains("=== General Purpose ===");
        assertThat(help).contains("=== Scale-Based ===");
        assertThat(help).contains("=== Industry-Specific ===");
        assertThat(help).contains("conservative");
        assertThat(help).contains("Conservative approach");
        assertThat(help).contains("retail");
        assertThat(help).contains("Optimized for retail");
    }

    @Test
    void shouldSuggestAppropriateProfiles() {
        // When
        String suggestions = MigrationProfile.suggestProfile(3, 8, 5);
        
        // Then
        assertThat(suggestions).contains("Profile suggestions based on your application:");
        assertThat(suggestions).contains("startup-aggressive");
        assertThat(suggestions).contains("discovery");
    }

    @Test
    void shouldSuggestProfilesForLargeApplications() {
        // When
        String suggestions = MigrationProfile.suggestProfile(100, 50, 200);
        
        // Then
        assertThat(suggestions).contains("enterprise-conservative");
        assertThat(suggestions).contains("conservative");
    }

    @Test
    void shouldSuggestAggressiveForLowFrequency() {
        // When
        String suggestions = MigrationProfile.suggestProfile(10, 20, 3);
        
        // Then
        assertThat(suggestions).contains("aggressive");
        assertThat(suggestions).contains("Your low frequencies suggest aggressive thresholds");
    }

    @Test
    void shouldSuggestConservativeForManyPatterns() {
        // When
        String suggestions = MigrationProfile.suggestProfile(20, 100, 50);
        
        // Then
        assertThat(suggestions).contains("conservative");
        assertThat(suggestions).contains("Many patterns detected");
    }
}