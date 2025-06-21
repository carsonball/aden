package org.carball.aden.model.analysis;

public enum NoSQLTarget {
    DYNAMODB("Amazon DynamoDB"),
    DOCUMENTDB("Amazon DocumentDB"),
    NEPTUNE("Amazon Neptune");

    private final String displayName;

    NoSQLTarget(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}