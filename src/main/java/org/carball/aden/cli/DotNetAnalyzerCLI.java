package org.carball.aden.cli;

import lombok.extern.slf4j.Slf4j;
import org.carball.aden.analyzer.DotNetAnalyzer;
import org.carball.aden.config.DotNetAnalyzerConfig;
import org.carball.aden.config.OutputFormat;
import org.carball.aden.config.ThresholdConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.carball.aden.model.analysis.AnalysisResult;
import org.carball.aden.model.analysis.NoSQLTarget;
import org.carball.aden.model.query.QueryStoreQuery;
import org.carball.aden.model.query.QueryStoreAnalysis;
import org.carball.aden.model.query.QualifiedMetrics;
import org.carball.aden.model.recommendation.NoSQLRecommendation;
import org.carball.aden.output.MigrationReport;
import org.carball.aden.parser.QueryStoreAnalyzer;
import org.carball.aden.parser.QueryStoreFileConnector;
import org.carball.aden.terraform.DynamoDBTerraformGenerator;
import org.carball.aden.terraform.model.TerraformOutput;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class DotNetAnalyzerCLI {

    private static final String VERSION = "1.0.0";
    private static final String BANNER = """
        ╔═══════════════════════════════════════════════════════════════╗
        ║     .NET Framework to AWS NoSQL Migration Analyzer v%s     ║
        ╚═══════════════════════════════════════════════════════════════╝
        """;

    public static void main(String[] args) {
        System.out.printf((BANNER) + "%n", VERSION);

        if (args.length < 2 || isHelpRequested(args)) {
            printUsage();
            System.exit(args.length < 2 ? 1 : 0);
        }

        try {
            DotNetAnalyzerConfig config = parseArgs(args);
            String queryStoreFile = getQueryStoreFile(args);

            System.out.println("\n🔍 Starting analysis...");
            System.out.println("   Schema file: " + config.getSchemaFile());
            System.out.println("   Source directory: " + config.getSourceDirectory());
            
            if (config.getOutputFormat() == OutputFormat.BOTH) {
                String baseFileName = removeFileExtension(config.getOutputFile());
                System.out.println("   Output: " + baseFileName + ".json, " + baseFileName + ".md");
            } else {
                System.out.println("   Output: " + config.getOutputFile());
            }
            if (queryStoreFile != null) {
                System.out.println("   Query Store: enabled (from export file)");
            }
            System.out.println();

            DotNetAnalyzer analyzer = new DotNetAnalyzer(config);

            // Step 1: Analyze Query Store first if export file provided
            QueryStoreAnalysis productionMetrics = null;
            if (queryStoreFile != null) {
                System.out.print("📈 Analyzing Query Store production metrics... ");
                productionMetrics = analyzeQueryStoreFromFile(queryStoreFile, config);
                System.out.println("✓");
            }
            
            // Step 2: Analyze static code with production metrics
            System.out.print("📊 Analyzing .NET Framework application... ");
            AnalysisResult result = analyzer.analyze(productionMetrics);
            System.out.println("✓");

            // Step 3: Generate recommendations with optional production metrics
            System.out.print("🤖 Generating AWS migration recommendations... ");
            List<NoSQLRecommendation> recommendations = productionMetrics != null ?
                analyzer.generateRecommendations(result, null, null, productionMetrics) :
                analyzer.generateRecommendations(result);
            System.out.println("✓");

            // Step 4: Generate Terraform scripts if requested
            boolean terraformFlag = hasTerraformFlag(args);
            if (terraformFlag) {
                System.out.print("🏗️ Generating Terraform infrastructure scripts... ");
                generateTerraformScripts(recommendations, config);
                System.out.println("✓");
            }
            
            // Step 5: Output results
            System.out.print("📝 Writing results... ");
            outputResults(result, recommendations, config);
            System.out.println("✓");

            // Print summary
            printSummary(result, recommendations);

            System.out.println("\n✅ Analysis complete!");
            
            if (config.getOutputFormat() == OutputFormat.BOTH) {
                String baseFileName = removeFileExtension(config.getOutputFile());
                System.out.println("   Output files:");
                System.out.println("     - " + baseFileName + ".json");
                System.out.println("     - " + baseFileName + ".md");
                if (terraformFlag && !recommendations.isEmpty()) {
                    System.out.println("     - main.tf");
                    System.out.println("     - variables.tf");
                    System.out.println("     - outputs.tf");
                }
            } else {
                System.out.println("   Output file: " + config.getOutputFile());
                if (terraformFlag && !recommendations.isEmpty()) {
                    System.out.println("   Terraform files:");
                    System.out.println("     - main.tf");
                    System.out.println("     - variables.tf");
                    System.out.println("     - outputs.tf");
                }
            }
            
            // Provide suggestions if no candidates found
            if (recommendations.isEmpty()) {
                System.out.println("\n💡 No migration candidates found.");
            }

        } catch (IllegalArgumentException e) {
            System.err.println("\n❌ Configuration error: " + e.getMessage());
            System.err.println("\nRun with --help for usage information.");
            log.debug("Configuration error details", e);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("\n❌ IO error: " + e.getMessage());
            log.debug("IO error details", e);
            System.exit(1);
        } catch (Exception e) {
            System.err.println("\n❌ Unexpected error: " + e.getMessage());
            log.debug("Unexpected error details", e);
            System.exit(1);
        }
    }

    private static boolean isHelpRequested(String[] args) {
        return Arrays.asList(args).contains("--help") ||
                Arrays.asList(args).contains("-h") ||
                Arrays.asList(args).contains("help");
    }

    private static void printUsage() {
        System.out.println("\nUsage: java -jar dotnet-analyzer.jar <schema-file> <source-directory> [options]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  schema-file         SQL Server schema DDL file (.sql)");
        System.out.println("  source-directory    Directory containing .NET Framework source code");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --output, -o        Output file for recommendations (default: recommendations.json)");
        System.out.println("  --format, -f        Output format: json|markdown|both (default: json)");
        System.out.println("  --api-key           OpenAI API key (or set OPENAI_API_KEY env var)");
        System.out.println("  --target            AWS target: dynamodb|documentdb|neptune|all (default: all)");
        System.out.println("  --query-store-file  JSON file exported from Query Store (secure alternative)");
        System.out.println("  --thresholds        YAML file with custom analysis thresholds (optional)");
        System.out.println("  --terraform         Generate Terraform infrastructure scripts");
        System.out.println("  --verbose, -v       Enable verbose output");
        System.out.println("  --help, -h          Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Basic analysis");
        System.out.println("  java -jar dotnet-analyzer.jar schema.sql ./src/MyApp.Data/");
        System.out.println();
        System.out.println("  # Include Query Store production metrics (secure file-based approach)");
        System.out.println("  java -jar dotnet-analyzer.jar schema.sql ./src/ --query-store-file query-store-export.json");
        System.out.println();
        System.out.println("  # Use custom analysis thresholds");
        System.out.println("  java -jar dotnet-analyzer.jar schema.sql ./src/ --thresholds my-thresholds.yml");
        System.out.println();
        System.out.println("  # Generate Terraform infrastructure scripts");
        System.out.println("  java -jar dotnet-analyzer.jar schema.sql ./src/ --terraform");
        System.out.println();
        System.out.println("Environment Variables:");
        System.out.println("  OPENAI_API_KEY      Your OpenAI API key for AI-powered recommendations");
        System.out.println();
        System.out.println("For more information, visit: https://github.com/your-org/dotnet-analyzer");
    }

    private static DotNetAnalyzerConfig parseArgs(String[] args) {
        DotNetAnalyzerConfig config = new DotNetAnalyzerConfig();
        config.setSchemaFile(Paths.get(args[0]));
        config.setSourceDirectory(Paths.get(args[1]));

        // Set defaults
        config.setOutputFile("recommendations.json");
        config.setOutputFormat(OutputFormat.JSON);
        config.setTargetServices(Arrays.asList(NoSQLTarget.values()));
        config.setVerbose(false);
        
        // Extract threshold config path and load thresholds
        String thresholdConfigPath = extractThresholdConfigPath(args);
        ThresholdConfig thresholds = loadThresholdConfig(thresholdConfigPath);
        config.setThresholdConfig(thresholds);

        // Parse optional arguments
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--output":
                case "-o":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Output file not specified");
                    }
                    config.setOutputFile(args[++i]);
                    break;

                case "--format":
                case "-f":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Output format not specified");
                    }
                    try {
                        OutputFormat format = OutputFormat.valueOf(args[++i].toUpperCase());
                        config.setOutputFormat(format);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid output format. Use: json, markdown, or both");
                    }
                    break;

                case "--api-key":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("API key not specified");
                    }
                    config.setOpenAiApiKey(args[++i]);
                    break;

                case "--target":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Target service not specified");
                    }
                    config.setTargetServices(parseTargetServices(args[++i]));
                    break;


                case "--verbose":
                case "-v":
                    config.setVerbose(true);
                    break;


                case "--query-store-file":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Query Store export file not specified");
                    }
                    // Just skip the value here, it will be handled by getQueryStoreFile()
                    i++;
                    break;
                
                case "--thresholds":
                case "--threshold-config":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Threshold config file not specified");
                    }
                    // Just skip the value here, it will be handled by DotNetAnalyzer constructor
                    i++;
                    break;
                    
                case "--terraform":
                    // Boolean flag, no value to consume
                    break;

                default:
                    throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        
        // Apply correct file extension based on format
        String outputFile = config.getOutputFile();
        String baseFileName = removeFileExtension(outputFile);
        
        switch (config.getOutputFormat()) {
            case MARKDOWN:
                config.setOutputFile(baseFileName + ".md");
                break;
            case BOTH:
            case JSON:
            default:
                config.setOutputFile(baseFileName + ".json");
                break;
        }

        // Validate configuration
        validateConfig(config);

        return config;
    }
    
    private static String removeFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            // Check if this is a path with directories
            int lastSeparatorIndex = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
            if (lastDotIndex > lastSeparatorIndex) {
                return filename.substring(0, lastDotIndex);
            }
        }
        return filename;
    }

    private static List<NoSQLTarget> parseTargetServices(String targets) {
        if (targets.equalsIgnoreCase("all")) {
            return Arrays.asList(NoSQLTarget.values());
        }

        return Arrays.stream(targets.split(","))
                .map(String::trim)
                .map(target -> {
                    try {
                        return NoSQLTarget.valueOf(target.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid target service: " + target);
                    }
                })
                .collect(Collectors.toList());
    }

    private static void validateConfig(DotNetAnalyzerConfig config) {
        // Validate schema file
        if (!Files.exists(config.getSchemaFile())) {
            throw new IllegalArgumentException("Schema file not found: " + config.getSchemaFile());
        }

        if (!config.getSchemaFile().toString().endsWith(".sql")) {
            throw new IllegalArgumentException("Schema file must be a .sql file");
        }

        // Validate source directory
        if (!Files.exists(config.getSourceDirectory())) {
            throw new IllegalArgumentException("Source directory not found: " + config.getSourceDirectory());
        }

        if (!Files.isDirectory(config.getSourceDirectory())) {
            throw new IllegalArgumentException("Source path must be a directory");
        }

        // Validate API key (only if not skipping AI)
        boolean skipAi = "true".equals(System.getProperty("skip.ai"));
        if (!skipAi && config.getOpenAiApiKey() == null) {
            config.setOpenAiApiKey(System.getenv("OPENAI_API_KEY"));
            if (config.getOpenAiApiKey() == null) {
                throw new IllegalArgumentException(
                        "OpenAI API key required. Use --api-key or set OPENAI_API_KEY environment variable. " +
                                "To skip AI recommendations, use -Dskip.ai=true"
                );
            }
        }

        // Validate output file
        Path outputDir = Paths.get(config.getOutputFile()).getParent();
        if (outputDir != null && !Files.exists(outputDir)) {
            throw new IllegalArgumentException("Output directory does not exist: " + outputDir);
        }
    }

    private static void outputResults(AnalysisResult result,
                                      List<NoSQLRecommendation> recommendations,
                                      DotNetAnalyzerConfig config) throws IOException {

        MigrationReport report = new MigrationReport(result, recommendations);
        String baseFileName = removeFileExtension(config.getOutputFile());

        if (config.getOutputFormat() == OutputFormat.JSON || config.getOutputFormat() == OutputFormat.BOTH) {
            String jsonOutput = report.toJson();
            String jsonFile = config.getOutputFormat() == OutputFormat.BOTH ? 
                baseFileName + ".json" : config.getOutputFile();
            Files.writeString(Paths.get(jsonFile), jsonOutput);
        }

        if (config.getOutputFormat() == OutputFormat.MARKDOWN || config.getOutputFormat() == OutputFormat.BOTH) {
            String markdownOutput = report.toMarkdown();
            String markdownFile = config.getOutputFormat() == OutputFormat.BOTH ? 
                baseFileName + ".md" : config.getOutputFile();
            Files.writeString(Paths.get(markdownFile), markdownOutput);
        }
    }

    private static void printSummary(AnalysisResult result, List<NoSQLRecommendation> recommendations) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 ANALYSIS SUMMARY");
        System.out.println("=".repeat(60));

        System.out.println("\nEntities analyzed: " + result.getUsageProfiles().size());
        System.out.println("Query patterns found: " + result.getQueryPatterns().size());
        System.out.println("Denormalization candidates: " + result.getDenormalizationCandidates().size());

        // Migration complexity breakdown
        long lowComplexity = result.getDenormalizationCandidates().stream()
                .filter(c -> c.getComplexity().toString().equals("LOW"))
                .count();
        long mediumComplexity = result.getDenormalizationCandidates().stream()
                .filter(c -> c.getComplexity().toString().equals("MEDIUM"))
                .count();
        long highComplexity = result.getDenormalizationCandidates().stream()
                .filter(c -> c.getComplexity().toString().equals("HIGH"))
                .count();

        System.out.println("\nComplexity breakdown:");
        System.out.println("  🟢 Low complexity: " + lowComplexity);
        System.out.println("  🟡 Medium complexity: " + mediumComplexity);
        System.out.println("  🔴 High complexity: " + highComplexity);

        // Top recommendations
        System.out.println("\n🎯 Top Migration Recommendations:");
        System.out.println("-".repeat(60));

        recommendations.stream()
                .limit(3)
                .forEach(rec -> {
                    System.out.printf("%-20s → %-15s%n",
                            rec.getPrimaryEntity(),
                            rec.getTargetService().getDisplayName()
                    );

                    if (rec.getPartitionKey() != null) {
                        System.out.printf("  └─ Partition Key: %s%n", rec.getPartitionKey().getAttribute());
                    }
                    if (rec.getSortKey() != null) {
                        System.out.printf("  └─ Sort Key: %s%n", rec.getSortKey().getAttribute());
                    }
                    System.out.println();
                });

        
        if (recommendations.isEmpty()) {
            System.out.println("\n💡 No migration candidates found.");
            System.out.println("See the report for detailed analysis and suggestions.");
        }
    }
    
    private static String getQueryStoreFile(String[] args) {
        // Check command line args for --query-store-file
        for (int i = 0; i < args.length - 1; i++) {
            if ("--query-store-file".equals(args[i])) {
                return args[i + 1];
            }
        }
        
        return null;
    }
    
    private static QueryStoreAnalysis analyzeQueryStoreFromFile(String queryStoreFile, DotNetAnalyzerConfig config) throws IOException {
        try {
            // Validate file exists
            Path filePath = Paths.get(queryStoreFile);
            if (!Files.exists(filePath)) {
                throw new IllegalArgumentException("Query Store export file not found: " + queryStoreFile);
            }
            
            // Load data from file
            QueryStoreFileConnector connector = new QueryStoreFileConnector(queryStoreFile);
            connector.loadData();
            
            // Extract database name from metadata
            QueryStoreFileConnector.ExportMetadata metadata = connector.getExportMetadata();
            String databaseName = metadata.getDatabaseName();
            
            // Get queries from file
            List<QueryStoreQuery> queries = connector.getAllQueries();
            
            if (queries.isEmpty()) {
                log.warn("No queries found in Query Store export file");
                return null;
            }
            
            // Analyze the queries
            QueryStoreAnalyzer analyzer = new QueryStoreAnalyzer();
            QueryStoreAnalysis analysisResult = analyzer.analyze(queries, databaseName);
            
            // Log summary if verbose
            if (config.isVerbose()) {
                System.out.println("     - Loaded " + queries.size() + " queries from export file");
                System.out.println("     - Database: " + databaseName);
                System.out.println("     - Export timestamp: " + metadata.getExportTimestamp());
                QualifiedMetrics metrics = analysisResult.getQualifiedMetrics();
                if (metrics != null) {
                    long totalExec = metrics.getTotalExecutions();
                    System.out.println("     - Total executions: " + String.format("%,d", totalExec));
                }
            }
            
            return analysisResult;
            
        } catch (Exception e) {
            log.error("Failed to analyze Query Store from file", e);
            throw new IOException("Query Store file analysis failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract threshold config path from command line arguments.
     */
    private static String extractThresholdConfigPath(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--threshold-config".equals(args[i]) || "--thresholds".equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }
    
    /**
     * Load threshold configuration from file or use defaults.
     */
    private static ThresholdConfig loadThresholdConfig(String configPath) {
        if (configPath == null || configPath.trim().isEmpty()) {
            log.info("No threshold config path provided, using discovery defaults");
            return ThresholdConfig.createDiscoveryDefaults();
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            File configFile = new File(configPath);
            
            if (!configFile.exists()) {
                log.warn("Threshold config file not found: {}, using discovery defaults", configPath);
                return ThresholdConfig.createDiscoveryDefaults();
            }
            
            ThresholdConfig config = mapper.readValue(configFile, ThresholdConfig.class);
            log.info("Loaded threshold configuration from: {}", configPath);
            return config;
            
        } catch (IOException e) {
            log.error("Failed to load threshold config from {}: {}, using discovery defaults", 
                     configPath, e.getMessage());
            return ThresholdConfig.createDiscoveryDefaults();
        }
    }
    
    private static boolean hasTerraformFlag(String[] args) {
        return Arrays.asList(args).contains("--terraform");
    }
    
    private static void generateTerraformScripts(List<NoSQLRecommendation> recommendations, 
                                                 DotNetAnalyzerConfig config) throws IOException {
        DynamoDBTerraformGenerator generator = new DynamoDBTerraformGenerator();
        
        // Extract project name from source directory for naming
        String projectName = config.getSourceDirectory().getFileName().toString();
        
        // Generate Terraform files
        TerraformOutput terraformOutput = generator.generateTerraform(recommendations, projectName);
        
        // Determine output directory (same as report location)
        Path outputDir = Paths.get(config.getOutputFile()).getParent();
        if (outputDir == null) {
            outputDir = Paths.get(".");
        }
        
        // Write Terraform files
        if (!terraformOutput.getMainTf().isEmpty()) {
            Path mainTfPath = outputDir.resolve("main.tf");
            Files.writeString(mainTfPath, terraformOutput.getMainTf());
        }
        
        if (!terraformOutput.getVariablesTf().isEmpty()) {
            Path variablesTfPath = outputDir.resolve("variables.tf");
            Files.writeString(variablesTfPath, terraformOutput.getVariablesTf());
        }
        
        if (!terraformOutput.getOutputsTf().isEmpty()) {
            Path outputsTfPath = outputDir.resolve("outputs.tf");
            Files.writeString(outputsTfPath, terraformOutput.getOutputsTf());
        }
        
        // Format Terraform files if terraform is available
        formatTerraformFiles(outputDir);
    }
    
    private static void formatTerraformFiles(Path outputDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("terraform", "fmt");
            pb.directory(outputDir.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.debug("Terraform files formatted successfully");
            } else {
                log.debug("terraform fmt exited with code {}, files may not be formatted", exitCode);
            }
        } catch (Exception e) {
            // Silently ignore if terraform is not available or fmt fails
            log.debug("Could not format Terraform files (terraform may not be available): {}", e.getMessage());
        }
    }

}