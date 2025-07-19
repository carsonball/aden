package org.carball.aden.cli;

import lombok.extern.slf4j.Slf4j;
import org.carball.aden.analyzer.DotNetAnalyzer;
import org.carball.aden.config.*;
import org.carball.aden.model.analysis.AnalysisResult;
import org.carball.aden.model.analysis.NoSQLTarget;
import org.carball.aden.model.query.QueryPattern;
import org.carball.aden.model.query.QueryStoreQuery;
import org.carball.aden.model.recommendation.NoSQLRecommendation;
import org.carball.aden.output.MigrationReport;
import org.carball.aden.parser.QueryStoreAnalyzer;
import org.carball.aden.parser.QueryStoreConnector;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
            if (Arrays.asList(args).contains("--help-profiles")) {
                System.out.println(MigrationProfile.getProfileHelp());
            } else if (Arrays.asList(args).contains("--help-thresholds")) {
                System.out.println(ConfigurationLoader.getThresholdHelp());
            } else {
                printUsage();
            }
            System.exit(args.length < 2 ? 1 : 0);
        }

        try {
            DotNetAnalyzerConfig config = parseArgs(args);
            String connectionString = getConnectionString(args);

            System.out.println("\n🔍 Starting analysis...");
            System.out.println("   Schema file: " + config.getSchemaFile());
            System.out.println("   Source directory: " + config.getSourceDirectory());
            System.out.println("   Output: " + config.getOutputFile());
            if (config.getMigrationProfile() != null) {
                System.out.println("   Migration profile: " + config.getMigrationProfile());
            }
            if (connectionString != null) {
                System.out.println("   Query Store: enabled");
            }
            System.out.println();

            DotNetAnalyzer analyzer = new DotNetAnalyzer(config, args);

            // Step 1: Analyze static code
            System.out.print("📊 Analyzing .NET Framework application... ");
            AnalysisResult result = analyzer.analyze();
            System.out.println("✓");
            
            // Step 2: Analyze Query Store if connection string provided
            Map<String, Object> productionMetrics = null;
            if (connectionString != null) {
                System.out.print("📈 Analyzing Query Store production metrics... ");
                productionMetrics = analyzeQueryStore(connectionString, config);
                System.out.println("✓");
            }

            // Step 3: Generate recommendations with optional production metrics
            System.out.print("🤖 Generating AWS migration recommendations... ");
            List<NoSQLRecommendation> recommendations = productionMetrics != null ?
                analyzer.generateRecommendations(result, null, null, productionMetrics) :
                analyzer.generateRecommendations(result);
            System.out.println("✓");

            // Step 3: Output results
            System.out.print("📝 Writing results... ");
            outputResults(result, recommendations, config);
            System.out.println("✓");

            // Print summary
            printSummary(result, recommendations);

            System.out.println("\n✅ Analysis complete!");
            System.out.println("   Output file: " + config.getOutputFile());

            if (config.getOutputFormat() == OutputFormat.BOTH) {
                String markdownFile = config.getOutputFile().replace(".json", ".md");
                System.out.println("   Markdown report: " + markdownFile);
            }
            
            // Provide profile suggestions if no candidates found
            if (recommendations.isEmpty()) {
                System.out.println("\n💡 No migration candidates found. This might be due to threshold settings.");
                System.out.println("   Consider using a different profile:");
                System.out.println("   - For small applications: --profile startup-aggressive");
                System.out.println("   - For discovery: --profile discovery");
                System.out.println("   - Or use --help-thresholds for manual tuning");
            }

        } catch (IllegalArgumentException e) {
            System.err.println("\n❌ Configuration error: " + e.getMessage());
            System.err.println("\nRun with --help for usage information.");
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
                Arrays.asList(args).contains("help") ||
                Arrays.asList(args).contains("--help-profiles") ||
                Arrays.asList(args).contains("--help-thresholds");
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
        System.out.println("  --complexity        Include only: low|medium|high|all (default: all)");
        System.out.println("  --query-store       SQL Server connection string for Query Store analysis");
        System.out.println("  --verbose, -v       Enable verbose output");
        System.out.println("  --help, -h          Show this help message");
        System.out.println();
        System.out.println("Migration Configuration:");
        System.out.println("  --profile           Migration profile: " + MigrationProfile.getAvailableProfiles());
        System.out.println("  --help-profiles     Show detailed profile information");
        System.out.println("  --help-thresholds   Show threshold configuration options");
        System.out.println();
        System.out.println("Threshold Overrides (see --help-thresholds for full list):");
        System.out.println("  --thresholds.high-frequency <num>     High frequency threshold");
        System.out.println("  --thresholds.medium-frequency <num>   Medium frequency threshold");
        System.out.println("  --thresholds.read-write-ratio <num>   Read/write ratio threshold");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Basic analysis");
        System.out.println("  java -jar dotnet-analyzer.jar schema.sql ./src/MyApp.Data/");
        System.out.println();
        System.out.println("  # Use startup profile for small applications");
        System.out.println("  java -jar dotnet-analyzer.jar schema.sql ./src/ --profile startup-aggressive");
        System.out.println();
        System.out.println("  # Override specific thresholds");
        System.out.println("  java -jar dotnet-analyzer.jar schema.sql ./src/ --thresholds.high-frequency 10");
        System.out.println();
        System.out.println("  # Include Query Store production metrics (JDBC format required)");
        System.out.println("  java -jar dotnet-analyzer.jar schema.sql ./src/ --query-store \"jdbc:sqlserver://localhost:1433;databaseName=TestApp;user=sa;password=Pass123;trustServerCertificate=true\"");
        System.out.println();
        System.out.println("Environment Variables:");
        System.out.println("  OPENAI_API_KEY                Your OpenAI API key for AI-powered recommendations");
        System.out.println("  ADEN_CONNECTION_STRING        SQL Server connection string (alternative to --query-store)");
        System.out.println("  ADEN_HIGH_FREQUENCY_THRESHOLD  Override high frequency threshold");
        System.out.println("  ADEN_MEDIUM_FREQUENCY_THRESHOLD Override medium frequency threshold");
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
        config.setComplexityFilter(ComplexityFilter.ALL);
        config.setVerbose(false);

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
                        config.setOutputFormat(OutputFormat.valueOf(args[++i].toUpperCase()));
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

                case "--complexity":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Complexity filter not specified");
                    }
                    try {
                        config.setComplexityFilter(ComplexityFilter.valueOf(args[++i].toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid complexity filter. Use: low, medium, high, or all");
                    }
                    break;

                case "--verbose":
                case "-v":
                    config.setVerbose(true);
                    break;

                case "--profile":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Profile name not specified");
                    }
                    config.setMigrationProfile(args[++i]);
                    break;

                case "--query-store":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Connection string not specified");
                    }
                    // Just skip the value here, it will be handled by getConnectionString()
                    i++;
                    break;

                case "--help-profiles":
                case "--help-thresholds":
                    // These are handled in the help check above
                    break;

                default:
                    // Check if it's a threshold override
                    if (args[i].startsWith("--thresholds.")) {
                        // Skip this argument and its value - will be handled by ConfigurationLoader
                        if (i + 1 < args.length) {
                            i++; // Skip the value
                        }
                    } else {
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                    }
            }
        }

        // Validate configuration
        validateConfig(config);

        return config;
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

        if (config.getOutputFormat() == OutputFormat.JSON || config.getOutputFormat() == OutputFormat.BOTH) {
            String jsonOutput = report.toJson();
            Files.writeString(Paths.get(config.getOutputFile()), jsonOutput);
        }

        if (config.getOutputFormat() == OutputFormat.MARKDOWN || config.getOutputFormat() == OutputFormat.BOTH) {
            String markdownFile = config.getOutputFile().replace(".json", ".md");
            String markdownOutput = report.toMarkdown();
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
                    System.out.printf("%-20s → %-15s %s%n",
                            rec.getPrimaryEntity(),
                            rec.getTargetService().getDisplayName(),
                            "(" + rec.getEstimatedCostSaving() + ")"
                    );

                    if (rec.getPartitionKey() != null) {
                        System.out.printf("  └─ Partition Key: %s%n", rec.getPartitionKey().getAttribute());
                    }
                    if (rec.getSortKey() != null) {
                        System.out.printf("  └─ Sort Key: %s%n", rec.getSortKey().getAttribute());
                    }
                    System.out.println();
                });

        // Overall complexity score
        if (result.getComplexityAnalysis() != null) {
            System.out.println("Overall migration complexity score: " +
                    result.getComplexityAnalysis().getOverallComplexity());
        }
        
        if (recommendations.isEmpty()) {
            System.out.println("\n🔧 Configuration Suggestions:");
            System.out.println("-".repeat(60));
            
            int maxFreq = result.getQueryPatterns().stream()
                    .mapToInt(QueryPattern::getFrequency)
                    .max()
                    .orElse(0);
            
            System.out.println(MigrationProfile.suggestProfile(
                    result.getUsageProfiles().size(),
                    result.getQueryPatterns().size(),
                    maxFreq));
        }
    }
    
    private static String getConnectionString(String[] args) {
        // First check command line args
        for (int i = 0; i < args.length - 1; i++) {
            if ("--query-store".equals(args[i])) {
                return args[i + 1];
            }
        }
        
        // Then check environment variable
        String envConnectionString = System.getenv("ADEN_CONNECTION_STRING");
        if (envConnectionString != null && !envConnectionString.isEmpty()) {
            log.debug("Using connection string from ADEN_CONNECTION_STRING environment variable");
            return envConnectionString;
        }
        
        return null;
    }
    
    private static Map<String, Object> analyzeQueryStore(String connectionString, DotNetAnalyzerConfig config) throws IOException {
        try {
            // Validate JDBC format
            if (!connectionString.startsWith("jdbc:sqlserver://")) {
                throw new IllegalArgumentException(
                    "Connection string must be in JDBC format. " +
                    "Example: jdbc:sqlserver://localhost:1433;databaseName=TestApp;user=sa;password=Pass123;trustServerCertificate=true"
                );
            }
            
            // Extract database name from connection string
            String databaseName = extractDatabaseName(connectionString);
            
            // Connect and extract Query Store data
            QueryStoreConnector connector = new QueryStoreConnector(connectionString);
            List<QueryStoreQuery> queries = connector.getAllQueries();
            
            if (queries.isEmpty()) {
                log.warn("No queries found in Query Store");
                return null;
            }
            
            // Analyze the queries
            QueryStoreAnalyzer analyzer = new QueryStoreAnalyzer();
            File tempFile = File.createTempFile("query_store_analysis_", ".json");
            tempFile.deleteOnExit();
            
            analyzer.analyzeAndExport(queries, tempFile.getAbsolutePath(), databaseName);
            
            // Read the analysis results
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> analysisResult = mapper.readValue(tempFile, Map.class);
            
            // Log summary if verbose
            if (config.isVerbose()) {
                System.out.println("     - Analyzed " + queries.size() + " queries from Query Store");
                @SuppressWarnings("unchecked")
                Map<String, Object> metrics = (Map<String, Object>) analysisResult.get("qualifiedMetrics");
                if (metrics != null) {
                    Object totalExec = metrics.get("totalExecutions");
                    if (totalExec != null) {
                        System.out.println("     - Total executions: " + String.format("%,d", ((Number) totalExec).longValue()));
                    }
                }
            }
            
            return analysisResult;
            
        } catch (Exception e) {
            log.error("Failed to analyze Query Store", e);
            throw new IOException("Query Store analysis failed: " + e.getMessage(), e);
        }
    }
    
    private static String extractDatabaseName(String connectionString) {
        // Extract from JDBC format - look for databaseName=
        String[] parts = connectionString.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase().startsWith("databasename=")) {
                return trimmed.substring(trimmed.indexOf('=') + 1).trim();
            }
        }
        return "Unknown";
    }
}