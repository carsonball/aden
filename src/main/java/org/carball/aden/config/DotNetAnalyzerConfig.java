package org.carball.aden.config;

import lombok.Data;
import java.nio.file.Path;

@Data
public class DotNetAnalyzerConfig {
    private Path schemaFile;
    private Path sourceDirectory;
    private String outputFile;
    private OutputFormat outputFormat;
    private String openAiApiKey;
    private boolean verbose;
    private ThresholdConfig thresholdConfig;
}