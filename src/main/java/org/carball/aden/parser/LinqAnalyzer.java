package org.carball.aden.parser;

import lombok.extern.slf4j.Slf4j;
import org.carball.aden.model.query.QueryPattern;
import org.carball.aden.model.query.QueryType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
public class LinqAnalyzer {

    private static final Pattern INCLUDE_PATTERN =
            Pattern.compile("\\.Include\\s*\\(\\s*(?:\\w+\\s*=>\\s*)?\\w+\\.(\\w+)\\s*\\)");

    private static final Pattern CHAINED_INCLUDE_PATTERN =
            Pattern.compile("(\\.Include\\s*\\([^)]+\\)(?:\\s*\\.ThenInclude\\s*\\([^)]+\\))*){2,}");

    private static final Pattern THEN_INCLUDE_PATTERN =
            Pattern.compile("\\.ThenInclude\\s*\\(\\s*(?:\\w+\\s*=>\\s*)?\\w+\\.(\\w+)\\s*\\)");

    private static final Map<QueryType, Pattern> QUERY_TYPE_PATTERNS = new HashMap<>();

    static {
        QUERY_TYPE_PATTERNS.put(QueryType.SINGLE_ENTITY,
                Pattern.compile("\\.(?:FirstOrDefault|First|SingleOrDefault|Single|Find)\\s*\\("));
        QUERY_TYPE_PATTERNS.put(QueryType.COLLECTION,
                Pattern.compile("\\.(?:ToList|ToArray|AsEnumerable|AsQueryable)\\s*\\("));
        QUERY_TYPE_PATTERNS.put(QueryType.FILTERED_SINGLE,
                Pattern.compile("\\.Where\\s*\\([^)]+\\)\\s*\\.(?:FirstOrDefault|First|SingleOrDefault|Single)\\s*\\("));
        QUERY_TYPE_PATTERNS.put(QueryType.FILTERED_COLLECTION,
                Pattern.compile("\\.Where\\s*\\([^)]+\\)\\s*\\.(?:ToList|ToArray)\\s*\\("));
    }

    private static final Pattern CONTEXT_PATTERN =
            Pattern.compile("(\\w+Context|_context|context)\\.(\\w+)(?:\\s*\\.AsNoTracking\\(\\))?");

    public List<QueryPattern> analyzeLinqPatterns(Path sourceDirectory) throws IOException {
        List<QueryPattern> patterns = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            paths.filter(this::isCSharpFile)
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            patterns.addAll(extractPatternsFromFile(content, path));
                        } catch (IOException e) {
                            log.error("Error analyzing file: {} - {}", path, e.getMessage());
                        }
                    });
        }

        return consolidatePatterns(patterns);
    }

    private boolean isCSharpFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".cs") &&
                !fileName.endsWith(".Designer.cs") &&
                !fileName.endsWith(".g.cs");
    }

    private List<QueryPattern> extractPatternsFromFile(String content, Path filePath) {
        List<QueryPattern> patterns = new ArrayList<>();

        // Find all EF context access points
        Matcher contextMatcher = CONTEXT_PATTERN.matcher(content);

        while (contextMatcher.find()) {
            String contextVar = contextMatcher.group(1);
            String entitySet = contextMatcher.group(2);

            // Extract the query starting from this point
            int queryStart = contextMatcher.start();
            String querySubstring = extractQuerySubstring(content, queryStart);

            // Analyze Include patterns
            patterns.addAll(analyzeIncludePatterns(querySubstring, entitySet, filePath.toString()));

            // Analyze query types
            QueryType queryType = determineQueryType(querySubstring);
            if (queryType != null) {
                QueryPattern pattern = new QueryPattern(
                        queryType.toString(),
                        entitySet,
                        1,
                        filePath.toString()
                );
                pattern.setQueryType(queryType);
                pattern.setPattern(querySubstring.length() > 100 ?
                        querySubstring.substring(0, 100) + "..." : querySubstring);
                patterns.add(pattern);
            }
        }

        return patterns;
    }

    private String extractQuerySubstring(String content, int start) {
        // Extract query until semicolon or new statement
        int end = start;
        int parenthesesCount = 0;

        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '(') parenthesesCount++;
            else if (c == ')') parenthesesCount--;
            else if (c == ';' && parenthesesCount == 0) {
                end = i;
                break;
            } else if (c == '\n' && parenthesesCount == 0 && i > start + 1) {
                // Check if this is really the end of the query
                String nextLine = content.substring(i + 1, Math.min(i + 50, content.length())).trim();
                if (!nextLine.startsWith(".")) {
                    end = i;
                    break;
                }
            }
        }

        return content.substring(start, end == start ? Math.min(start + 500, content.length()) : end);
    }

    private List<QueryPattern> analyzeIncludePatterns(String queryString, String entitySet, String sourceFile) {
        List<QueryPattern> patterns = new ArrayList<>();

        // Check for chained includes (complex eager loading)
        Matcher chainedMatcher = CHAINED_INCLUDE_PATTERN.matcher(queryString);
        if (chainedMatcher.find()) {
            String fullChain = chainedMatcher.group(0);
            int includeCount = countOccurrences(fullChain, ".Include");

            QueryPattern pattern = new QueryPattern(
                    "COMPLEX_EAGER_LOADING",
                    entitySet,
                    1,
                    sourceFile
            );
            pattern.setQueryType(QueryType.COMPLEX_EAGER_LOADING);
            pattern.setPattern(fullChain);
            patterns.add(pattern);

            log.trace("Found complex eager loading with {} includes for {}", includeCount, entitySet);
        }

        // Individual Include patterns
        Matcher includeMatcher = INCLUDE_PATTERN.matcher(queryString);
        while (includeMatcher.find()) {
            String includedProperty = includeMatcher.group(1);

            QueryPattern pattern = new QueryPattern(
                    "EAGER_LOADING",
                    entitySet + "." + includedProperty,
                    1,
                    sourceFile
            );
            pattern.setQueryType(QueryType.EAGER_LOADING);
            patterns.add(pattern);
        }

        // ThenInclude patterns
        Matcher thenIncludeMatcher = THEN_INCLUDE_PATTERN.matcher(queryString);
        while (thenIncludeMatcher.find()) {
            String includedProperty = thenIncludeMatcher.group(1);

            QueryPattern pattern = new QueryPattern(
                    "NESTED_EAGER_LOADING",
                    entitySet + ".nested." + includedProperty,
                    1,
                    sourceFile
            );
            pattern.setQueryType(QueryType.EAGER_LOADING);
            patterns.add(pattern);
        }

        return patterns;
    }

    private QueryType determineQueryType(String queryString) {
        for (Map.Entry<QueryType, Pattern> entry : QUERY_TYPE_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(queryString).find()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private List<QueryPattern> consolidatePatterns(List<QueryPattern> rawPatterns) {
        Map<String, QueryPattern> consolidated = new HashMap<>();

        for (QueryPattern pattern : rawPatterns) {
            String key = pattern.getType() + ":" + pattern.getTargetEntity();
            QueryPattern existing = consolidated.get(key);

            if (existing != null) {
                existing.incrementFrequency();
                existing.addSourceFile(pattern.getSourceFiles().get(0));
            } else {
                consolidated.put(key, pattern);
            }
        }

        // Sort by frequency
        List<QueryPattern> result = new ArrayList<>(consolidated.values());
        result.sort((a, b) -> Integer.compare(b.getFrequency(), a.getFrequency()));

        return result;
    }

    private int countOccurrences(String text, String search) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(search, index)) != -1) {
            count++;
            index += search.length();
        }
        return count;
    }
}