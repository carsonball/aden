package org.carball.aden.model.analysis;

public enum MigrationComplexity {
    LOW(0, 30),
    MEDIUM(31, 60),
    HIGH(61, 100);

    private final int minScore;
    private final int maxScore;

    MigrationComplexity(int minScore, int maxScore) {
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public static MigrationComplexity fromScore(int score) {
        for (MigrationComplexity complexity : values()) {
            if (score >= complexity.minScore && score <= complexity.maxScore) {
                return complexity;
            }
        }
        return HIGH;
    }
}