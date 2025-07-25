package org.carball.aden.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public enum MigrationProfile {
    
    // General purpose profiles
    CONSERVATIVE("conservative", "Conservative approach - only migrate obvious candidates",
            2.0, 0.5, 2.0, 180, 120, 80, 50),
    
    BALANCED("balanced", "Balanced approach - default settings for most applications",
            1.0, 1.0, 1.0, 150, 100, 60, 30),
    
    AGGRESSIVE("aggressive", "Aggressive approach - identify more migration opportunities",
            0.4, 1.5, 0.7, 120, 80, 40, 20),
    
    DISCOVERY("discovery", "Discovery mode - find all potential patterns",
            0.2, 2.0, 0.5, 100, 60, 30, 10),
    
    // Scale-based profiles
    STARTUP_AGGRESSIVE("startup-aggressive", "Optimized for small applications with growth potential",
            0.3, 1.8, 0.6, 100, 60, 30, 15),
    
    SMB_BALANCED("smb-balanced", "Balanced approach for small-to-medium business applications",
            0.6, 1.2, 0.8, 130, 85, 50, 25),
    
    ENTERPRISE_CONSERVATIVE("enterprise-conservative", "Conservative approach for large enterprise systems",
            1.8, 0.8, 1.5, 200, 150, 100, 60),
    
    // Industry-specific profiles
    RETAIL("retail", "Optimized for retail/e-commerce patterns with seasonal spikes",
            0.7, 1.3, 0.6, 140, 90, 50, 25) {
        @Override
        public MigrationThresholds buildThresholds() {
            MigrationThresholds base = super.buildThresholds();
            return base.toBuilder()
                    .highReadWriteRatio(5.0) // Lower read/write ratio for retail
                    .complexQueryRequirement(3) // More sensitive to complex queries
                    .build();
        }
    },
    
    HEALTHCARE("healthcare", "Conservative approach for healthcare systems with complex relationships",
            1.2, 0.9, 1.8, 160, 110, 70, 40) {
        @Override
        public MigrationThresholds buildThresholds() {
            MigrationThresholds base = super.buildThresholds();
            return base.toBuilder()
                    .complexityPenaltyMultiplier(1.5) // Higher penalty for complexity in healthcare
                    .minimumMigrationScore(40) // Higher minimum score for safety
                    .build();
        }
    },
    
    MANUFACTURING("manufacturing", "Handles complex ERP patterns with many-to-many relationships",
            0.8, 1.1, 1.2, 130, 85, 50, 30) {
        @Override
        public MigrationThresholds buildThresholds() {
            MigrationThresholds base = super.buildThresholds();
            return base.toBuilder()
                    .complexRelationshipPenalty(5) // Lower penalty for complex relationships
                    .complexQueryRequirement(4) // Manufacturing often has complex queries
                    .build();
        }
    },
    
    FINANCIAL("financial", "Conservative approach for financial systems with regulatory requirements",
            2.5, 0.7, 2.0, 200, 140, 90, 60) {
        @Override
        public MigrationThresholds buildThresholds() {
            MigrationThresholds base = super.buildThresholds();
            return base.toBuilder()
                    .highReadWriteRatio(15.0) // Very high read/write expectations
                    .minimumMigrationScore(60) // High confidence required
                    .build();
        }
    };
    
    private final String name;
    private final String description;
    private final double frequencyMultiplier;
    private final double complexityMultiplier;
    private final double readWriteMultiplier;
    private final int excellentThreshold;
    private final int strongThreshold;
    private final int goodThreshold;
    private final int fairThreshold;
    
    MigrationProfile(String name, String description, 
                    double frequencyMultiplier, double complexityMultiplier, double readWriteMultiplier,
                    int excellentThreshold, int strongThreshold, int goodThreshold, int fairThreshold) {
        this.name = name;
        this.description = description;
        this.frequencyMultiplier = frequencyMultiplier;
        this.complexityMultiplier = complexityMultiplier;
        this.readWriteMultiplier = readWriteMultiplier;
        this.excellentThreshold = excellentThreshold;
        this.strongThreshold = strongThreshold;
        this.goodThreshold = goodThreshold;
        this.fairThreshold = fairThreshold;
    }
    
    /**
     * Creates MigrationThresholds based on this profile's settings.
     */
    public MigrationThresholds buildThresholds() {
        // Base values that profiles multiply against
        int baseHighFreq = 50;
        int baseMediumFreq = 20;
        double baseReadWriteRatio = 10.0;
        int baseComplexPenalty = 10;
        int baseComplexQueryReq = 5;
        int baseCoAccessThreshold = 500;
        long baseHighProductionExecution = 1000;
        
        return MigrationThresholds.builder()
                .profileName(name)
                .profileDescription(description)
                .highFrequencyThreshold((int) (baseHighFreq * frequencyMultiplier))
                .mediumFrequencyThreshold((int) (baseMediumFreq * frequencyMultiplier))
                .highReadWriteRatio(baseReadWriteRatio * readWriteMultiplier)
                .complexRelationshipPenalty((int) (baseComplexPenalty * complexityMultiplier))
                .complexityPenaltyMultiplier(complexityMultiplier)
                .complexQueryRequirement((int) (baseComplexQueryReq * frequencyMultiplier))
                .productionCoAccessThreshold((int) (baseCoAccessThreshold * frequencyMultiplier))
                .highProductionExecutionThreshold((long) (baseHighProductionExecution * frequencyMultiplier))
                .excellentCandidateThreshold(excellentThreshold)
                .strongCandidateThreshold(strongThreshold)
                .goodCandidateThreshold(goodThreshold)
                .fairCandidateThreshold(fairThreshold)
                .minimumMigrationScore(fairThreshold)
                .build();
    }
    
    /**
     * Finds profile by name (case-insensitive).
     */
    public static MigrationProfile fromName(String name) {
        for (MigrationProfile profile : values()) {
            if (profile.getName().equalsIgnoreCase(name)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Unknown migration profile: " + name + 
                ". Available profiles: " + getAvailableProfiles());
    }
    
    /**
     * Returns a comma-separated list of available profile names.
     */
    public static String getAvailableProfiles() {
        StringBuilder sb = new StringBuilder();
        for (MigrationProfile profile : values()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(profile.getName());
        }
        return sb.toString();
    }
    
    /**
     * Returns detailed information about all available profiles.
     */
    public static String getProfileHelp() {
        StringBuilder help = new StringBuilder();
        help.append("Available Migration Profiles:\n\n");
        
        help.append("=== General Purpose ===\n");
        addProfileHelp(help, CONSERVATIVE, BALANCED, AGGRESSIVE, DISCOVERY);
        
        help.append("\n=== Scale-Based ===\n");
        addProfileHelp(help, STARTUP_AGGRESSIVE, SMB_BALANCED, ENTERPRISE_CONSERVATIVE);
        
        help.append("\n=== Industry-Specific ===\n");
        addProfileHelp(help, RETAIL, HEALTHCARE, MANUFACTURING, FINANCIAL);
        
        help.append("\nUse --profile=<name> to select a profile, or --help-thresholds for detailed threshold information.\n");
        
        return help.toString();
    }
    
    private static void addProfileHelp(StringBuilder help, MigrationProfile... profiles) {
        for (MigrationProfile profile : profiles) {
            help.append(String.format("  %-20s %s\n", profile.getName(), profile.getDescription()));
        }
    }
    
    /**
     * Suggests appropriate profiles based on application characteristics.
     */
    public static String suggestProfile(int entityCount, int queryPatternCount, int maxFrequency) {
        StringBuilder suggestions = new StringBuilder();
        suggestions.append("Profile suggestions based on your application:\n");
        
        if (entityCount <= 5) {
            suggestions.append("  - startup-aggressive: Good for small applications\n");
            suggestions.append("  - discovery: Find all patterns in small codebases\n");
        } else if (entityCount <= 20) {
            suggestions.append("  - smb-balanced: Balanced approach for medium applications\n");
            suggestions.append("  - balanced: Default choice for most applications\n");
        } else {
            suggestions.append("  - enterprise-conservative: Focus on high-impact migrations\n");
            suggestions.append("  - conservative: Only migrate obvious candidates\n");
        }
        
        if (maxFrequency < 10) {
            suggestions.append("  - aggressive: Your low frequencies suggest aggressive thresholds\n");
        }
        
        if (queryPatternCount > 50) {
            suggestions.append("  - conservative: Many patterns detected, focus on best candidates\n");
        }
        
        return suggestions.toString();
    }
}