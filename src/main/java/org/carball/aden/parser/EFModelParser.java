package org.carball.aden.parser;

import lombok.extern.slf4j.Slf4j;
import org.carball.aden.model.entity.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
public class EFModelParser {

    private final Map<String, String> dbSetPropertyToEntityMap = new HashMap<>();

    private static final Pattern CLASS_PATTERN =
            Pattern.compile("public\\s+(?:partial\\s+)?class\\s+(\\w+)(?:\\s*:\\s*([\\w\\s,]+))?");

    private static final Pattern NAVIGATION_COLLECTION_PATTERN =
            Pattern.compile("public\\s+virtual\\s+(?:ICollection|IList|List|HashSet)<(\\w+)>\\s+(\\w+)");

    private static final Pattern NAVIGATION_REFERENCE_PATTERN =
            Pattern.compile("public\\s+virtual\\s+(\\w+)\\s+(\\w+)\\s*\\{\\s*get;\\s*set;\\s*}");

    private static final Pattern FOREIGN_KEY_ATTRIBUTE_PATTERN =
            Pattern.compile("\\[ForeignKey\\(\"(\\w+)\"\\)]");

    private static final Pattern DATA_ANNOTATION_PATTERN =
            Pattern.compile("\\[(\\w+)(?:\\(([^)]+)\\))?]");

    private static final Pattern DBSET_PATTERN =
            Pattern.compile("public\\s+DbSet<(\\w+)>\\s+(\\w+)\\s*\\{\\s*get;\\s*set;\\s*}");
            
    private static final Pattern TOTABLE_PATTERN = 
            Pattern.compile("modelBuilder\\.Entity<(\\w+)>\\(\\)\\s*\\.ToTable\\(\"(\\w+)\"\\)");
            
    private final Map<String, String> entityToTableMappings = new HashMap<>();

    private static final List<String> PRIMITIVE_TYPES = Arrays.asList(
            "string", "int", "long", "bool", "boolean", "DateTime", "DateTimeOffset",
            "decimal", "double", "float", "Guid", "byte", "short", "char",
            "int?", "long?", "bool?", "boolean?", "DateTime?", "DateTimeOffset?",
            "decimal?", "double?", "float?", "Guid?", "byte?", "short?", "char?"
    );

    public List<EntityModel> parseEntities(Path sourceDirectory) throws IOException {
        List<EntityModel> entities = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            paths.filter(this::isCSharpFile)
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            
                            // Parse DbContext files for DbSet mappings
                            if (isDbContextFile(content)) {
                                parseDbContextMappings(content, path.getFileName().toString());
                            }
                            
                            EntityModel entity = parseEntityFromContent(content, path.getFileName().toString());
                            if (entity != null && !entity.getNavigationProperties().isEmpty()) {
                                entities.add(entity);
                                log.debug("Parsed entity: {} with {} navigation properties",
                                        entity.getClassName(), entity.getNavigationProperties().size());
                            }
                        } catch (IOException e) {
                            log.error("Error parsing file: {} - {}", path, e.getMessage());
                        }
                    });
        }

        // Post-process to determine entity types
        determineEntityTypes(entities);
        
        // Apply ToTable mappings to entities that were parsed before DbContext
        for (EntityModel entity : entities) {
            if (entity.getTableName() == null) {
                String mappedTableName = entityToTableMappings.get(entity.getClassName());
                if (mappedTableName != null) {
                    entity.setTableName(mappedTableName);
                }
            }
        }

        return entities;
    }

    private boolean isCSharpFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".cs") &&
                !fileName.endsWith(".Designer.cs") &&
                !fileName.endsWith(".g.cs");
    }

    EntityModel parseEntityFromContent(String content, String fileName) {
        // Skip if it's likely not an entity class
        if (!content.contains("public") || !content.contains("class")) {
            return null;
        }

        Matcher classMatcher = CLASS_PATTERN.matcher(content);

        if (!classMatcher.find()) {
            return null;
        }

        String className = classMatcher.group(1);
        String inheritance = classMatcher.group(2);

        // Skip if it's likely a service or controller
        if (className.endsWith("Service") || className.endsWith("Controller") ||
                className.endsWith("Repository") || className.endsWith("Context")) {
            return null;
        }

        EntityModel entity = new EntityModel(className, fileName);

        // Extract navigation properties
        extractNavigationProperties(content, entity);

        // Extract data annotations
        extractDataAnnotations(content, entity);
        
        // Set table name from [Table] annotation if present
        setTableNameFromAnnotations(entity);

        // Check inheritance
        if (inheritance != null && inheritance.contains("DbContext")) {
            return null; // This is the context, not an entity
        }

        return entity;
    }

    private void extractNavigationProperties(String content, EntityModel entity) {
        // Collection navigation properties (one-to-many)
        Matcher collectionMatcher = NAVIGATION_COLLECTION_PATTERN.matcher(content);
        while (collectionMatcher.find()) {
            String targetEntity = collectionMatcher.group(1);
            String propertyName = collectionMatcher.group(2);

            NavigationProperty navProp = new NavigationProperty(
                    propertyName, targetEntity, NavigationType.ONE_TO_MANY);
            entity.addNavigationProperty(navProp);

            log.trace("Found collection navigation: {} -> {}", propertyName, targetEntity);
        }

        // Reference navigation properties (many-to-one or one-to-one)
        Matcher referenceMatcher = NAVIGATION_REFERENCE_PATTERN.matcher(content);
        while (referenceMatcher.find()) {
            String targetEntity = referenceMatcher.group(1);
            String propertyName = referenceMatcher.group(2);

            // Skip primitive types and common non-entity types
            if (isPrimitiveOrCommonType(targetEntity)) {
                continue;
            }

            // Check if there's a foreign key annotation to determine relationship type
            NavigationType navType = NavigationType.MANY_TO_ONE;

            // Look for [ForeignKey] attribute near this property
            int propertyPos = referenceMatcher.start();
            String beforeProperty = content.substring(Math.max(0, propertyPos - 200), propertyPos);
            if (beforeProperty.contains("[ForeignKey")) {
                navType = NavigationType.MANY_TO_ONE;
            } else if (beforeProperty.contains("[OneToOne") || beforeProperty.contains("[Required")) {
                navType = NavigationType.ONE_TO_ONE;
            }

            NavigationProperty navProp = new NavigationProperty(
                    propertyName, targetEntity, navType);
            entity.addNavigationProperty(navProp);

            log.trace("Found reference navigation: {} -> {} ({})", propertyName, targetEntity, navType);
        }
    }

    private void extractDataAnnotations(String content, EntityModel entity) {
        Matcher annotationMatcher = DATA_ANNOTATION_PATTERN.matcher(content);

        while (annotationMatcher.find()) {
            String annotationName = annotationMatcher.group(1);
            String annotationValue = annotationMatcher.group(2);

            // Only keep relevant EF annotations
            if (isRelevantAnnotation(annotationName)) {
                DataAnnotation annotation = new DataAnnotation(annotationName,
                        annotationValue != null ? annotationValue : "");
                entity.addAnnotation(annotation);
            }
        }
    }

    private boolean isPrimitiveOrCommonType(String typeName) {
        return PRIMITIVE_TYPES.contains(typeName) ||
                typeName.startsWith("Dictionary") ||
                typeName.startsWith("List") ||
                typeName.startsWith("IEnumerable") ||
                typeName.startsWith("ICollection") ||
                typeName.startsWith("Task") ||
                typeName.endsWith("Dto") ||
                typeName.endsWith("ViewModel") ||
                typeName.endsWith("Model");
    }

    private boolean isRelevantAnnotation(String annotationName) {
        return Arrays.asList(
                "Table", "Column", "Key", "ForeignKey", "Required",
                "MaxLength", "StringLength", "Index", "NotMapped",
                "InverseProperty", "ComplexType"
        ).contains(annotationName);
    }

    private void determineEntityTypes(List<EntityModel> entities) {
        for (EntityModel entity : entities) {
            // Entities with no incoming references are likely aggregate roots
            boolean hasIncomingReferences = false;

            for (EntityModel other : entities) {
                if (other == entity) continue;

                for (NavigationProperty navProp : other.getNavigationProperties()) {
                    if (navProp.getTargetEntity().equals(entity.getClassName()) &&
                            navProp.getType() == NavigationType.ONE_TO_MANY) {
                        hasIncomingReferences = true;
                        break;
                    }
                }

                if (hasIncomingReferences) break;
            }

            if (!hasIncomingReferences) {
                entity.setType(EntityType.AGGREGATE_ROOT);
            } else if (entity.getNavigationProperties().isEmpty()) {
                entity.setType(EntityType.VALUE_OBJECT);
            } else if (entity.getNavigationProperties().size() == 1) {
                entity.setType(EntityType.CHILD_ENTITY);
            } else {
                entity.setType(EntityType.LOOKUP);
            }
        }
    }

    private boolean isDbContextFile(String content) {
        return content.contains("DbContext") && content.contains("DbSet<");
    }

    private void parseDbContextMappings(String content, String fileName) {
        // Parse DbSet properties
        Matcher matcher = DBSET_PATTERN.matcher(content);
        while (matcher.find()) {
            String entityType = matcher.group(1);
            String propertyName = matcher.group(2);
            dbSetPropertyToEntityMap.put(propertyName, entityType);
            log.debug("Found DbSet mapping: {} -> {} in {}", propertyName, entityType, fileName);
        }
        
        // Parse ToTable mappings from OnModelCreating
        Matcher toTableMatcher = TOTABLE_PATTERN.matcher(content);
        while (toTableMatcher.find()) {
            String entityType = toTableMatcher.group(1);
            String tableName = toTableMatcher.group(2);
            entityToTableMappings.put(entityType, tableName);
            log.debug("Found ToTable mapping: {} -> {} in {}", entityType, tableName, fileName);
        }
    }

    public Map<String, String> getDbSetPropertyToEntityMap() {
        return new HashMap<>(dbSetPropertyToEntityMap);
    }
    
    public Map<String, String> getEntityToTableMappings() {
        return new HashMap<>(entityToTableMappings);
    }
    
    private void setTableNameFromAnnotations(EntityModel entity) {
        // First check if we have a ToTable mapping from OnModelCreating
        String mappedTableName = entityToTableMappings.get(entity.getClassName());
        if (mappedTableName != null) {
            entity.setTableName(mappedTableName);
            return;
        }
        
        // Otherwise check for [Table] annotation
        for (DataAnnotation annotation : entity.getAnnotations()) {
            if ("Table".equals(annotation.getName()) && !annotation.getValue().isEmpty()) {
                // Extract just the table name from the annotation value
                String value = annotation.getValue();
                String tableName;
                
                // Handle cases like: "TableName" or "TableName", Schema = "schema"
                if (value.contains(",")) {
                    // Extract just the table name part before the comma
                    tableName = value.substring(0, value.indexOf(",")).replace("\"", "").trim();
                } else {
                    // Simple case - just remove quotes
                    tableName = value.replace("\"", "").trim();
                }
                
                entity.setTableName(tableName);
                break;
            }
        }
    }
}