package org.carball.aden.analyzer;

import org.carball.aden.model.analysis.EntityUsageProfile;
import org.carball.aden.model.analysis.MigrationComplexity;
import org.carball.aden.model.entity.EntityModel;
import org.carball.aden.model.entity.NavigationType;

public class ComplexityScorer {

    private static final int NAVIGATION_PROPERTY_WEIGHT = 2;
    private static final int HIGH_FREQUENCY_WEIGHT = 10;
    private static final int MEDIUM_FREQUENCY_WEIGHT = 5;
    private static final int CIRCULAR_REFERENCE_PENALTY = 20;
    private static final int MANY_TO_MANY_PENALTY = 15;
    private static final int DEEP_NESTING_PENALTY = 8;

    public MigrationComplexity scoreEntity(EntityModel entity, EntityUsageProfile usageProfile) {
        int complexity = 0;

        // Factor 1: Number of navigation properties
        complexity += entity.getNavigationProperties().size() * NAVIGATION_PROPERTY_WEIGHT;

        // Factor 2: Usage frequency
        if (usageProfile.getEagerLoadingCount() > 100) {
            complexity += HIGH_FREQUENCY_WEIGHT;
        } else if (usageProfile.getEagerLoadingCount() > 50) {
            complexity += MEDIUM_FREQUENCY_WEIGHT;
        }

        // Factor 3: Circular references
        if (entity.hasCircularReferences()) {
            complexity += CIRCULAR_REFERENCE_PENALTY;
        }

        // Factor 4: Many-to-many relationships
        long manyToManyCount = entity.getNavigationProperties().stream()
                .filter(p -> p.getType() == NavigationType.MANY_TO_MANY)
                .count();
        complexity += manyToManyCount * MANY_TO_MANY_PENALTY;

        // Factor 5: Deep nesting (multiple levels of includes)
        if (hasDeepNesting(usageProfile)) {
            complexity += DEEP_NESTING_PENALTY;
        }

        // Factor 6: Number of always-loaded related entities
        complexity += usageProfile.getAlwaysLoadedWithEntities().size() * 3;

        return MigrationComplexity.fromScore(complexity);
    }

    private boolean hasDeepNesting(EntityUsageProfile profile) {
        return profile.getQueryPatterns().stream()
                .anyMatch(p -> p.getTargetEntity().split("\\.").length > 2);
    }

    public String generateComplexityReport(EntityModel entity,
                                           EntityUsageProfile profile,
                                           MigrationComplexity complexity) {
        StringBuilder report = new StringBuilder();

        report.append("Entity: ").append(entity.getClassName()).append("\n");
        report.append("Complexity: ").append(complexity).append("\n");
        report.append("Factors:\n");

        report.append("  - Navigation Properties: ").append(entity.getNavigationProperties().size()).append("\n");
        report.append("  - Eager Loading Frequency: ").append(profile.getEagerLoadingCount()).append("\n");
        report.append("  - Always Loaded With: ").append(profile.getAlwaysLoadedWithEntities().size()).append(" entities\n");

        if (entity.hasCircularReferences()) {
            report.append("  - Has Circular References: Yes\n");
        }

        long manyToMany = entity.getNavigationProperties().stream()
                .filter(p -> p.getType() == NavigationType.MANY_TO_MANY)
                .count();
        if (manyToMany > 0) {
            report.append("  - Many-to-Many Relationships: ").append(manyToMany).append("\n");
        }

        return report.toString();
    }
}