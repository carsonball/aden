package org.carball.aden.model.recommendation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Captures the AI's design rationale for NoSQL recommendations.
 * Provides detailed explanations of design decisions based on entity usage patterns.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignRationale {
    private String denormalizationStrategy;
    private String keyDesignJustification;
    private String relationshipHandling;
    private String performanceOptimizations;
    private String accessPatternAnalysis;
    private List<String> tradeoffsConsidered;
}