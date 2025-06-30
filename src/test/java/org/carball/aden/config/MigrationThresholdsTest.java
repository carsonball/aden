package org.carball.aden.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrationThresholdsTest {

    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(MigrationThresholds.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        logger.setLevel(Level.DEBUG); // Set to DEBUG to capture INFO messages too
        logger.setAdditive(false); // Prevent duplicate messages
    }

    @Test
    void shouldCreateDefaultThresholds() {
        // When
        MigrationThresholds thresholds = MigrationThresholds.defaults();
        
        // Then
        assertThat(thresholds.getHighFrequencyThreshold()).isEqualTo(50);
        assertThat(thresholds.getMediumFrequencyThreshold()).isEqualTo(20);
        assertThat(thresholds.getHighReadWriteRatio()).isEqualTo(10.0);
        assertThat(thresholds.getComplexRelationshipPenalty()).isEqualTo(10);
        assertThat(thresholds.getComplexityPenaltyMultiplier()).isEqualTo(1.0);
        assertThat(thresholds.getMinimumMigrationScore()).isEqualTo(30);
        assertThat(thresholds.getExcellentCandidateThreshold()).isEqualTo(150);
        assertThat(thresholds.getStrongCandidateThreshold()).isEqualTo(100);
        assertThat(thresholds.getGoodCandidateThreshold()).isEqualTo(60);
        assertThat(thresholds.getFairCandidateThreshold()).isEqualTo(30);
        assertThat(thresholds.getComplexQueryRequirement()).isEqualTo(5);
        assertThat(thresholds.getProfileName()).isEqualTo("default");
        assertThat(thresholds.getProfileDescription()).isEqualTo("Default balanced thresholds");
    }

    @Test
    void shouldValidateValidThresholds() {
        // Given
        MigrationThresholds thresholds = MigrationThresholds.builder()
                .highFrequencyThreshold(100)
                .mediumFrequencyThreshold(50)
                .highReadWriteRatio(5.0)
                .excellentCandidateThreshold(200)
                .strongCandidateThreshold(150)
                .goodCandidateThreshold(100)
                .fairCandidateThreshold(50)
                .complexityPenaltyMultiplier(1.0)
                .build();
        
        // When
        thresholds.validate();
        
        // Then - Should only have DEBUG message, no warnings
        assertThat(logAppender.list).hasSize(1);
        assertThat(logAppender.list.get(0).getLevel()).isEqualTo(Level.DEBUG);
    }

    @Test
    void shouldWarnWhenHighFrequencyNotGreaterThanMedium() {
        // Given
        MigrationThresholds thresholds = MigrationThresholds.builder()
                .highFrequencyThreshold(20)
                .mediumFrequencyThreshold(30) // Invalid: medium > high
                .build();
        
        // When
        thresholds.validate();
        
        // Then - Should have 1 WARN + 1 DEBUG message
        List<ILoggingEvent> logs = logAppender.list;
        assertThat(logs).hasSize(2);
        assertThat(logs.stream().anyMatch(log -> 
                log.getLevel() == Level.WARN && 
                log.getFormattedMessage().contains("High frequency threshold (20) should be greater than medium frequency threshold (30)")))
                .isTrue();
    }

    @Test
    void shouldWarnWhenMediumFrequencyIsNotPositive() {
        // Given
        MigrationThresholds thresholds = MigrationThresholds.builder()
                .mediumFrequencyThreshold(0)
                .build();
        
        // When
        thresholds.validate();
        
        // Then
        List<ILoggingEvent> logs = logAppender.list;
        assertThat(logs.stream().anyMatch(log -> 
                log.getLevel() == Level.WARN &&
                log.getFormattedMessage().contains("Medium frequency threshold (0) should be positive")))
                .isTrue();
    }

    @Test
    void shouldWarnWhenReadWriteRatioTooLow() {
        // Given
        MigrationThresholds thresholds = MigrationThresholds.builder()
                .highReadWriteRatio(0.5)
                .build();
        
        // When
        thresholds.validate();
        
        // Then
        List<ILoggingEvent> logs = logAppender.list;
        assertThat(logs.stream().anyMatch(log -> 
                log.getLevel() == Level.WARN &&
                log.getFormattedMessage().contains("High read/write ratio (0.5) should be greater than 1.0")))
                .isTrue();
    }

    @Test
    void shouldWarnWhenCandidateThresholdsAreInconsistent() {
        // Given
        MigrationThresholds thresholds = MigrationThresholds.builder()
                .excellentCandidateThreshold(100)
                .strongCandidateThreshold(150) // Invalid: strong > excellent  
                .goodCandidateThreshold(160)   // Invalid: good > strong (which is already > excellent)
                .fairCandidateThreshold(80)    // This will be valid since fair < good
                .build();
        
        // When
        thresholds.validate();
        
        // Then
        List<ILoggingEvent> logs = logAppender.list;
        assertThat(logs.stream().anyMatch(log -> 
                log.getLevel() == Level.WARN &&
                log.getFormattedMessage().contains("Excellent candidate threshold (100) should be greater than strong candidate threshold (150)")))
                .isTrue();
        assertThat(logs.stream().anyMatch(log -> 
                log.getLevel() == Level.WARN &&
                log.getFormattedMessage().contains("Strong candidate threshold (150) should be greater than good candidate threshold (160)")))
                .isTrue();
    }

    @Test
    void shouldWarnWhenComplexityPenaltyMultiplierIsNegative() {
        // Given
        MigrationThresholds thresholds = MigrationThresholds.builder()
                .complexityPenaltyMultiplier(-0.5)
                .build();
        
        // When
        thresholds.validate();
        
        // Then
        List<ILoggingEvent> logs = logAppender.list;
        assertThat(logs.stream().anyMatch(log -> 
                log.getLevel() == Level.WARN &&
                log.getFormattedMessage().contains("Complexity penalty multiplier (-0.5) should not be negative")))
                .isTrue();
    }

    @Test
    void shouldProvideConfigurationSummary() {
        // Given
        MigrationThresholds thresholds = MigrationThresholds.builder()
                .profileName("test-profile")
                .highFrequencyThreshold(25)
                .mediumFrequencyThreshold(10)
                .highReadWriteRatio(7.5)
                .minimumMigrationScore(15)
                .build();
        
        // When
        String summary = thresholds.getConfigurationSummary();
        
        // Then
        assertThat(summary).isEqualTo("Profile: test-profile | High freq: 25 | Medium freq: 10 | Read/Write: 7.5 | Min score: 15");
    }

    @Test
    void shouldSuggestAdjustmentsForLowFrequency() {
        // Given
        MigrationThresholds thresholds = MigrationThresholds.builder()
                .mediumFrequencyThreshold(20)
                .build();
        
        // When
        thresholds.suggestAdjustments(5, 10, 15); // maxFrequency=15 < mediumThreshold=20
        
        // Then
        List<ILoggingEvent> logs = logAppender.list;
        assertThat(logs.stream().anyMatch(log -> 
                log.getLevel() == Level.WARN &&
                log.getFormattedMessage().contains("Maximum query frequency (15) is below medium threshold (20)")))
                .isTrue();
        assertThat(logs.stream().anyMatch(log -> 
                log.getLevel() == Level.WARN &&
                log.getFormattedMessage().contains("Consider using --profile=aggressive or lowering thresholds")))
                .isTrue();
    }

    @Test
    void shouldSuggestProfileForSmallApplication() {
        // Given
        MigrationThresholds thresholds = MigrationThresholds.builder()
                .highFrequencyThreshold(50)
                .build();
        
        // When
        thresholds.suggestAdjustments(3, 8, 25); // entityCount=3 < 5
        
        // Then
        List<ILoggingEvent> logs = logAppender.list;
        assertThat(logs.stream().anyMatch(log -> 
                log.getLevel() == Level.INFO &&
                log.getFormattedMessage().contains("Small application detected (3 entities). Consider --profile=startup-aggressive")))
                .isTrue();
    }

    @Test
    void shouldSuggestProfileForLargeApplication() {
        // Given
        MigrationThresholds thresholds = MigrationThresholds.builder()
                .highFrequencyThreshold(20)
                .build();
        
        // When
        thresholds.suggestAdjustments(100, 50, 200); // entityCount=100 > 50
        
        // Then
        List<ILoggingEvent> logs = logAppender.list;
        assertThat(logs.stream().anyMatch(log -> 
                log.getLevel() == Level.INFO &&
                log.getFormattedMessage().contains("Large application detected (100 entities). Consider --profile=enterprise-conservative")))
                .isTrue();
    }

    @Test
    void shouldWarnAboutNoQueryPatterns() {
        // Given
        MigrationThresholds thresholds = MigrationThresholds.defaults();
        
        // When
        thresholds.suggestAdjustments(5, 0, 0); // totalQueryPatterns=0
        
        // Then
        List<ILoggingEvent> logs = logAppender.list;
        assertThat(logs.stream().anyMatch(log -> 
                log.getLevel() == Level.WARN &&
                log.getFormattedMessage().contains("No query patterns detected. Ensure your application contains LINQ queries with Include() statements")))
                .isTrue();
    }

    @Test
    void shouldCreateBuilderFromExistingThresholds() {
        // Given
        MigrationThresholds original = MigrationThresholds.builder()
                .profileName("original")
                .highFrequencyThreshold(100)
                .mediumFrequencyThreshold(50)
                .build();
        
        // When
        MigrationThresholds modified = original.toBuilder()
                .profileName("modified")
                .highFrequencyThreshold(200)
                .build();
        
        // Then
        assertThat(modified.getProfileName()).isEqualTo("modified");
        assertThat(modified.getHighFrequencyThreshold()).isEqualTo(200);
        assertThat(modified.getMediumFrequencyThreshold()).isEqualTo(50); // Preserved from original
        assertThat(original.getProfileName()).isEqualTo("original"); // Original unchanged
    }
}