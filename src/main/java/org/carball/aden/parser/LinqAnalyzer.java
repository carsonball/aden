package org.carball.aden.parser;

import lombok.extern.slf4j.Slf4j;
import org.carball.aden.model.query.FilterCondition;
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
            Pattern.compile("(?:\\s*\\.Include\\s*\\([^)]+\\)(?:\\s*\\.ThenInclude\\s*\\([^)]+\\))*){2,}", Pattern.DOTALL);

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
    
    // Enhanced patterns for WHERE clause analysis
    private static final Pattern WHERE_CLAUSE_PATTERN = 
            Pattern.compile("\\.Where\\s*\\(\\s*(?:\\w+\\s*=>\\s*)?([^)]+)\\)", Pattern.DOTALL);
    
    private static final Pattern ORDER_BY_PATTERN = 
            Pattern.compile("\\.OrderBy(?:Descending)?\\s*\\(\\s*(?:\\w+\\s*=>\\s*)?(?:\\w+\\.)?([\\w.]+)\\s*\\)");
    
    private static final Pattern COLUMN_COMPARISON_PATTERN = 
            Pattern.compile("(?:\\w+\\.)?([\\w.]+)\\s*(==|!=|>=|<=|>|<)\\s*([^\\s&|)]+)");
    
    private static final Pattern CONTAINS_PATTERN = 
            Pattern.compile("(?:\\w+\\.)?([\\w.]+)\\.Contains\\s*\\(([^)]+)\\)");
    
    private static final Pattern STARTS_WITH_PATTERN = 
            Pattern.compile("(?:\\w+\\.)?([\\w.]+)\\.StartsWith\\s*\\(([^)]+)\\)");
    
    private static final Pattern ENDS_WITH_PATTERN = 
            Pattern.compile("(?:\\w+\\.)?([\\w.]+)\\.EndsWith\\s*\\(([^)]+)\\)");
    
    private static final Pattern PARAMETER_PATTERN = 
            Pattern.compile("@(\\w+)|\\b([a-z][a-zA-Z0-9_]*[a-zA-Z0-9])\\b");
    
    private static final Pattern AGGREGATION_PATTERN = 
            Pattern.compile("\\.(Count|Sum|Average|Max|Min|Any|All)\\s*\\(");
    
    private static final Pattern PAGINATION_PATTERN = 
            Pattern.compile("\\.(Skip|Take)\\s*\\(([^)]+)\\)");
    
    private static final Pattern GROUP_BY_PATTERN = 
            Pattern.compile("\\.GroupBy\\s*\\(\\s*(?:\\w+\\s*=>\\s*)?(?:\\w+\\.)?([\\w.]+)\\s*\\)");

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
            String entitySet = contextMatcher.group(2);

            // Extract the query starting from this point
            int queryStart = contextMatcher.start();
            String querySubstring = extractQuerySubstring(content, queryStart);

            // Analyze Include patterns
            patterns.addAll(analyzeIncludePatterns(querySubstring, entitySet, filePath.toString()));

            // Analyze WHERE clause patterns
            patterns.addAll(analyzeWhereClausePatterns(querySubstring, entitySet, filePath.toString()));

            // Analyze ORDER BY patterns
            patterns.addAll(analyzeOrderByPatterns(querySubstring, entitySet, filePath.toString()));

            // Analyze aggregation patterns
            patterns.addAll(analyzeAggregationPatterns(querySubstring, entitySet, filePath.toString()));

            // Analyze pagination patterns
            patterns.addAll(analyzePaginationPatterns(querySubstring, entitySet, filePath.toString()));

            // Analyze GROUP BY patterns
            patterns.addAll(analyzeGroupByPatterns(querySubstring, entitySet, filePath.toString()));

            // Analyze query types
            QueryType queryType = determineQueryType(querySubstring);
            if (queryType != null) {
                QueryPattern pattern = new QueryPattern(
                        queryType.toString(),
                        entitySet,
                        filePath.toString()
                );
                pattern.setQueryType(queryType);
                pattern.setPattern(querySubstring.length() > 100 ?
                        querySubstring.substring(0, 100) + "..." : querySubstring);
                
                // Enhance pattern with detailed analysis
                enhancePatternWithDetails(pattern, querySubstring);
                patterns.add(pattern);
            }
        }

        return patterns;
    }

    private String extractQuerySubstring(String content, int start) {
        int end = start;
        int parenthesesCount = 0;

        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '(') parenthesesCount++;
            else if (c == ')') parenthesesCount--;
            else if (c == ';' && parenthesesCount == 0) {
                end = i;
                break;
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
                existing.addSourceFile(pattern.getSourceFiles().get(0));
            } else {
                consolidated.put(key, pattern);
            }
        }

        return new ArrayList<>(consolidated.values());
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
    
    /**
     * Analyzes WHERE clause patterns to extract column references and filter conditions
     */
    private List<QueryPattern> analyzeWhereClausePatterns(String queryString, String entitySet, String sourceFile) {
        List<QueryPattern> patterns = new ArrayList<>();
        
        Matcher whereMatcher = WHERE_CLAUSE_PATTERN.matcher(queryString);
        while (whereMatcher.find()) {
            String whereClause = whereMatcher.group(1);
            
            QueryPattern pattern = new QueryPattern(
                    "WHERE_CLAUSE",
                    entitySet,
                    sourceFile
            );
            pattern.setQueryType(QueryType.WHERE_CLAUSE);
            pattern.setPattern(whereClause);
            
            // Extract column comparisons
            extractColumnComparisons(whereClause, pattern);
            
            // Extract string operations
            extractStringOperations(whereClause, pattern);
            
            // Extract parameters
            extractParameters(whereClause, pattern);
            
            // Determine complexity
            pattern.setHasComplexWhere(isComplexWhereClause(whereClause));
            
            patterns.add(pattern);
        }
        
        return patterns;
    }
    
    /**
     * Analyzes ORDER BY patterns to extract sorting columns
     */
    private List<QueryPattern> analyzeOrderByPatterns(String queryString, String entitySet, String sourceFile) {
        List<QueryPattern> patterns = new ArrayList<>();
        
        Matcher orderMatcher = ORDER_BY_PATTERN.matcher(queryString);
        while (orderMatcher.find()) {
            String column = orderMatcher.group(1);
            
            QueryPattern pattern = new QueryPattern(
                    "ORDER_BY",
                    entitySet,
                    sourceFile
            );
            pattern.setQueryType(QueryType.ORDER_BY);
            pattern.addOrderByColumn(column);
            pattern.setPattern("ORDER BY " + column);
            
            patterns.add(pattern);
        }
        
        return patterns;
    }
    
    /**
     * Analyzes aggregation patterns
     */
    private List<QueryPattern> analyzeAggregationPatterns(String queryString, String entitySet, String sourceFile) {
        List<QueryPattern> patterns = new ArrayList<>();
        
        Matcher aggMatcher = AGGREGATION_PATTERN.matcher(queryString);
        while (aggMatcher.find()) {
            String aggFunction = aggMatcher.group(1);
            
            QueryPattern pattern = new QueryPattern(
                    "AGGREGATION",
                    entitySet,
                    sourceFile
            );
            pattern.setQueryType(QueryType.AGGREGATION);
            pattern.setHasAggregation(true);
            pattern.setPattern(aggFunction + "()");
            
            patterns.add(pattern);
        }
        
        return patterns;
    }
    
    /**
     * Analyzes pagination patterns
     */
    private List<QueryPattern> analyzePaginationPatterns(String queryString, String entitySet, String sourceFile) {
        List<QueryPattern> patterns = new ArrayList<>();
        
        Matcher pageMatcher = PAGINATION_PATTERN.matcher(queryString);
        while (pageMatcher.find()) {
            String method = pageMatcher.group(1);
            String value = pageMatcher.group(2);
            
            QueryPattern pattern = new QueryPattern(
                    "PAGINATION",
                    entitySet,
                    sourceFile
            );
            pattern.setQueryType(QueryType.PAGINATION);
            pattern.setHasPagination(true);
            pattern.setPattern(method + "(" + value + ")");
            
            patterns.add(pattern);
        }
        
        return patterns;
    }
    
    /**
     * Analyzes GROUP BY patterns
     */
    private List<QueryPattern> analyzeGroupByPatterns(String queryString, String entitySet, String sourceFile) {
        List<QueryPattern> patterns = new ArrayList<>();
        
        Matcher groupMatcher = GROUP_BY_PATTERN.matcher(queryString);
        while (groupMatcher.find()) {
            String column = groupMatcher.group(1);
            
            QueryPattern pattern = new QueryPattern(
                    "GROUP_BY",
                    entitySet,
                    sourceFile
            );
            pattern.setQueryType(QueryType.GROUP_BY);
            pattern.setPattern("GROUP BY " + column);
            
            patterns.add(pattern);
        }
        
        return patterns;
    }
    
    /**
     * Extracts column comparisons from WHERE clause
     */
    private void extractColumnComparisons(String whereClause, QueryPattern pattern) {
        Matcher compMatcher = COLUMN_COMPARISON_PATTERN.matcher(whereClause);
        while (compMatcher.find()) {
            String column = compMatcher.group(1);
            String operator = compMatcher.group(2);
            String value = compMatcher.group(3);
            
            pattern.addWhereClauseColumn(column);
            
            FilterCondition condition = FilterCondition.builder()
                    .column(column)
                    .operator(operator)
                    .valueType(determineValueType(value))
                    .isParameter(value.startsWith("@") || Character.isUpperCase(value.charAt(0)))
                    .build();
            
            pattern.addFilterCondition(condition);
        }
    }
    
    /**
     * Extracts string operations (Contains, StartsWith, EndsWith)
     */
    private void extractStringOperations(String whereClause, QueryPattern pattern) {
        // Contains
        Matcher containsMatcher = CONTAINS_PATTERN.matcher(whereClause);
        while (containsMatcher.find()) {
            String column = containsMatcher.group(1);
            String value = containsMatcher.group(2);
            
            pattern.addWhereClauseColumn(column);
            
            FilterCondition condition = FilterCondition.builder()
                    .column(column)
                    .operator("CONTAINS")
                    .valueType(determineValueType(value))
                    .isParameter(value.startsWith("@") || Character.isUpperCase(value.charAt(0)))
                    .build();
            
            pattern.addFilterCondition(condition);
        }
        
        // StartsWith
        Matcher startsWithMatcher = STARTS_WITH_PATTERN.matcher(whereClause);
        while (startsWithMatcher.find()) {
            String column = startsWithMatcher.group(1);
            String value = startsWithMatcher.group(2);
            
            pattern.addWhereClauseColumn(column);
            
            FilterCondition condition = FilterCondition.builder()
                    .column(column)
                    .operator("STARTS_WITH")
                    .valueType(determineValueType(value))
                    .isParameter(value.startsWith("@") || Character.isUpperCase(value.charAt(0)))
                    .build();
            
            pattern.addFilterCondition(condition);
        }
        
        // EndsWith
        Matcher endsWithMatcher = ENDS_WITH_PATTERN.matcher(whereClause);
        while (endsWithMatcher.find()) {
            String column = endsWithMatcher.group(1);
            String value = endsWithMatcher.group(2);
            
            pattern.addWhereClauseColumn(column);
            
            FilterCondition condition = FilterCondition.builder()
                    .column(column)
                    .operator("ENDS_WITH")
                    .valueType(determineValueType(value))
                    .isParameter(value.startsWith("@") || Character.isUpperCase(value.charAt(0)))
                    .build();
            
            pattern.addFilterCondition(condition);
        }
    }
    
    /**
     * Extracts parameters from WHERE clause
     */
    private void extractParameters(String whereClause, QueryPattern pattern) {
        Matcher paramMatcher = PARAMETER_PATTERN.matcher(whereClause);
        Set<String> foundParams = new HashSet<>();
        
        while (paramMatcher.find()) {
            String param = paramMatcher.group(1) != null ? paramMatcher.group(1) : paramMatcher.group(2);
            if (param != null && !foundParams.contains(param)) {
                pattern.addParameterType(param);
                foundParams.add(param);
            }
        }
    }
    
    /**
     * Determines if WHERE clause is complex (has multiple conditions, logical operators)
     */
    private boolean isComplexWhereClause(String whereClause) {
        // Check for logical operators
        boolean hasLogicalOperators = whereClause.contains("&&") || whereClause.contains("||") || 
                                    whereClause.contains("AND") || whereClause.contains("OR");
        
        // Check for multiple conditions
        int conditionCount = countOccurrences(whereClause, "==") + 
                           countOccurrences(whereClause, "!=") + 
                           countOccurrences(whereClause, ">=") + 
                           countOccurrences(whereClause, "<=") + 
                           countOccurrences(whereClause, ">") + 
                           countOccurrences(whereClause, "<") + 
                           countOccurrences(whereClause, "Contains") + 
                           countOccurrences(whereClause, "StartsWith") + 
                           countOccurrences(whereClause, "EndsWith");
        
        return hasLogicalOperators || conditionCount > 1;
    }
    
    /**
     * Determines the value type based on the value string
     */
    private String determineValueType(String value) {
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return "STRING";
        } else if (value.startsWith("'") && value.endsWith("'")) {
            return "STRING";
        } else if (value.startsWith("@") || Character.isLowerCase(value.charAt(0))) {
            return "PARAMETER";
        } else if (value.matches("\\d+")) {
            return "INTEGER";
        } else if (value.matches("\\d+\\.\\d+")) {
            return "DECIMAL";
        } else if (value.equals("true") || value.equals("false")) {
            return "BOOLEAN";
        } else if (value.contains("DateTime") || value.contains("Date")) {
            return "DATE";
        } else {
            return "UNKNOWN";
        }
    }
    
    /**
     * Enhances pattern with detailed analysis
     */
    private void enhancePatternWithDetails(QueryPattern pattern, String queryString) {
        // Check for aggregation
        if (AGGREGATION_PATTERN.matcher(queryString).find()) {
            pattern.setHasAggregation(true);
        }
        
        // Check for pagination
        if (PAGINATION_PATTERN.matcher(queryString).find()) {
            pattern.setHasPagination(true);
        }
        
        // Check for complex WHERE
        Matcher whereMatcher = WHERE_CLAUSE_PATTERN.matcher(queryString);
        if (whereMatcher.find()) {
            pattern.setHasComplexWhere(isComplexWhereClause(whereMatcher.group(1)));
        }
    }
}