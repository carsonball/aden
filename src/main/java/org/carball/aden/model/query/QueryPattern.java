package org.carball.aden.model.query;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class QueryPattern {
    private String type;
    private String targetEntity;
    private int frequency = 1;
    private List<String> sourceFiles = new ArrayList<>();
    private String pattern;
    private QueryType queryType;

    public QueryPattern(String type, String targetEntity, int frequency, String sourceFile) {
        this.type = type;
        this.targetEntity = targetEntity;
        this.frequency = frequency;
        this.sourceFiles.add(sourceFile);
    }

    public void incrementFrequency() {
        frequency++;
    }

    public void addSourceFile(String file) {
        if (!sourceFiles.contains(file)) {
            sourceFiles.add(file);
        }
    }
}