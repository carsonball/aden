package org.carball.aden.config;

import lombok.Data;
import org.carball.aden.model.analysis.NoSQLTarget;
import java.nio.file.Path;
import java.util.List;

@Data
public class DotNetAnalyzerConfig {
    private Path schemaFile;
    private Path sourceDirectory;
    private String outputFile;
    private OutputFormat outputFormat;
    private String openAiApiKey;
    private List<NoSQLTarget> targetServices;
    private ComplexityFilter complexityFilter;
    private boolean verbose;
    private boolean debugMode;
    
    // Migration threshold configuration
    private String migrationProfile;
}