package org.carball.aden.parser;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.table.*;
import org.carball.aden.model.schema.*;
import org.carball.aden.model.schema.Index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SchemaParser {

    private SchemaParser() {
        // Utility class - prevent instantiation
    }

    public static DatabaseSchema parseDDL(Path ddlFile) throws IOException {
        String content = Files.readString(ddlFile);
        return parseDDL(content);
    }

    public static DatabaseSchema parseDDL(String ddlContent) {
        DatabaseSchema schema = new DatabaseSchema();

        try {
            // Preprocess DDL to handle SQL Server bracket syntax
            String processedDDL = preprocessDDL(ddlContent);
            
            // Parse all statements in the DDL
            Statements statements = CCJSqlParserUtil.parseStatements(processedDDL);

            // First pass: Create all tables
            for (Statement statement : statements.getStatements()) {
                if (statement instanceof CreateTable createTable) {
                    Table table = convertTable(createTable);
                    schema.addTable(table);
                    log.debug("Parsed table: {}", table.getName());
                }
            }

            // Second pass: Extract relationships from foreign keys
            for (Statement statement : statements.getStatements()) {
                if (statement instanceof CreateTable createTable) {
                    extractRelationships(createTable, schema);
                }
            }

            // Third pass: Create MANY_TO_MANY relationships for junction tables
            createManyToManyRelationships(schema);

        } catch (JSQLParserException e) {
            log.error("Error parsing DDL: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid SQL DDL: " + e.getMessage(), e);
        }

        return schema;
    }

    private static String preprocessDDL(String ddlContent) {
        // Remove SQL Server bracket identifiers to make DDL compatible with JSqlParser
        // This handles cases like CREATE TABLE [Order] and REFERENCES [Order](Id)
        String processed = ddlContent.replaceAll("\\[([^]]+)]", "$1");
        
        // Handle SQL Server function calls in DEFAULT clauses that JSqlParser doesn't support
        // Replace DEFAULT (FUNCTION()) with DEFAULT 'FUNCTION()' to make it parseable
        processed = processed.replaceAll("DEFAULT\\s*\\(([A-Z_]+\\(\\))\\)", "DEFAULT '$1'");
        
        // Remove SQL Server specific keywords that JSqlParser doesn't understand
        // Remove CLUSTERED and NONCLUSTERED from PRIMARY KEY and other constraints
        processed = processed.replaceAll("\\bCLUSTERED\\b", "");
        processed = processed.replaceAll("\\bNONCLUSTERED\\b", "");
        
        // Clean up extra spaces that might result from keyword removal
        processed = processed.replaceAll("\\s+", " ");
        
        return processed;
    }

    private static Table convertTable(CreateTable createTable) {
        String tableName = cleanTableName(createTable.getTable());
        Table table = new Table(tableName);

        if (createTable.getColumnDefinitions() != null) {
            for (ColumnDefinition colDef : createTable.getColumnDefinitions()) {
                Column column = convertColumn(colDef);
                table.addColumn(column);
                
                // Create unique index for UNIQUE columns
                if (colDef.getColumnSpecs() != null && 
                    colDef.getColumnSpecs().stream().anyMatch(spec -> spec.equalsIgnoreCase("UNIQUE"))) {
                    String columnName = cleanIdentifier(colDef.getColumnName());
                    Index uniqueIndex = Index.builder()
                            .name("UQ_" + tableName + "_" + columnName)
                            .columns(List.of(columnName))
                            .unique(true)
                            .clustered(false)
                            .build();
                    table.addIndex(uniqueIndex);
                }
            }
        }

        // Process table constraints (primary keys, etc.)
        if (createTable.getIndexes() != null) {
            for (net.sf.jsqlparser.statement.create.table.Index index : createTable.getIndexes()) {
                if ("PRIMARY KEY".equalsIgnoreCase(index.getType())) {
                    processPrimaryKey(index, table);
                } else {
                    processIndex(index, table);
                }
            }
        }

        return table;
    }

    private static Column convertColumn(ColumnDefinition colDef) {
        Column.ColumnBuilder builder = Column.builder()
                .name(cleanIdentifier(colDef.getColumnName()))
                .dataType(colDef.getColDataType().getDataType())
                .nullable(true); // Default to nullable

        // Process column specifications
        if (colDef.getColumnSpecs() != null) {
            List<String> specs = colDef.getColumnSpecs();
            for (int i = 0; i < specs.size(); i++) {
                String spec = specs.get(i);
                String upperSpec = spec.toUpperCase();

                if (upperSpec.equals("NOT NULL")) {
                    builder.nullable(false);
                } else if (upperSpec.equals("NOT") && i + 1 < specs.size() && 
                      specs.get(i + 1).equalsIgnoreCase("NULL")) {
                    // Handle "NOT" "NULL" as separate specs
                    builder.nullable(false);
                } else if (upperSpec.equals("PRIMARY") || upperSpec.contains("PRIMARY KEY")) {
                    builder.primaryKey(true);
                } else if (upperSpec.equals("UNIQUE")) {
                    // Mark column as unique - we'll create a unique index for it
                    // This will be handled in the table processing
                } else if (upperSpec.contains("DEFAULT")) {
                    // Extract default value if available
                    builder.defaultValue(spec);
                } else if (upperSpec.contains("IDENTITY")) {
                    builder.defaultValue("IDENTITY");
                }
            }
        }

        return builder.build();
    }

    private static void processPrimaryKey(net.sf.jsqlparser.statement.create.table.Index index, Table table) {
        List<String> pkColumns = index.getColumns().stream()
                .map(net.sf.jsqlparser.statement.create.table.Index.ColumnParams::getColumnName)
                .map(SchemaParser::cleanIdentifier)
                .collect(Collectors.toList());

        if (pkColumns.size() == 1) {
            // Single column primary key
            String pkColumn = pkColumns.get(0);
            table.getColumns().stream()
                    .filter(c -> c.getName().equalsIgnoreCase(pkColumn))
                    .findFirst()
                    .ifPresent(c -> {
                        c.setPrimaryKey(true);
                        table.setPrimaryKey(c);
                    });
        } else {
            // Composite primary key - create as index
            Index compositeIndex =
                    Index.builder()
                            .name("PK_" + table.getName())
                            .columns(pkColumns)
                            .unique(true)
                            .clustered(true)
                            .build();
            table.addIndex(compositeIndex);
        }
    }

    private static void processIndex(net.sf.jsqlparser.statement.create.table.Index index, Table table) {
        List<String> indexColumns = index.getColumns().stream()
                .map(net.sf.jsqlparser.statement.create.table.Index.ColumnParams::getColumnName)
                .map(SchemaParser::cleanIdentifier)
                .collect(Collectors.toList());

        Index tableIndex =
                Index.builder()
                        .name(index.getName() != null ? index.getName() : "IX_" + String.join("_", indexColumns))
                        .columns(indexColumns)
                        .unique("UNIQUE".equalsIgnoreCase(index.getType()))
                        .clustered(index.getType() != null && index.getType().contains("CLUSTERED"))
                        .build();

        table.addIndex(tableIndex);
    }

    private static void extractRelationships(CreateTable createTable, DatabaseSchema schema) {
        String tableName = cleanTableName(createTable.getTable());

        // Check column definitions for foreign key constraints
        if (createTable.getColumnDefinitions() != null) {
            for (ColumnDefinition colDef : createTable.getColumnDefinitions()) {
                if (colDef.getColumnSpecs() != null) {
                    extractForeignKeyFromSpecs(tableName, colDef, schema);
                }
            }
        }

        // Check table-level foreign key constraints
        if (createTable.getIndexes() != null) {
            for (net.sf.jsqlparser.statement.create.table.Index index : createTable.getIndexes()) {
                if (index instanceof ForeignKeyIndex fkIndex) {
                    processForeignKey(tableName, fkIndex, schema);
                }
            }
        }
    }

    private static void extractForeignKeyFromSpecs(String tableName, ColumnDefinition colDef, DatabaseSchema schema) {
        String columnName = cleanIdentifier(colDef.getColumnName());

        List<String> specs = colDef.getColumnSpecs();
        for (int i = 0; i < specs.size(); i++) {
            String spec = specs.get(i);
            
            // Check for inline REFERENCES in single spec (legacy format) - only if it contains more than just "REFERENCES"
            if (spec.toUpperCase().contains("REFERENCES") && !spec.trim().equalsIgnoreCase("REFERENCES")) {
                // Parse REFERENCES clause within single spec
                String[] parts = spec.split("\\s+");
                for (int j = 0; j < parts.length - 1; j++) {
                    if (parts[j].equalsIgnoreCase("REFERENCES")) {
                        String referencedTable = cleanIdentifier(parts[j + 1]);
                        String referencedColumn = "Id"; // Default assumption

                        // Check if column is specified
                        if (j + 2 < parts.length && parts[j + 2].startsWith("(")) {
                            referencedColumn = cleanIdentifier(
                                    parts[j + 2].replaceAll("[()]", ""));
                        }

                        createForeignKeyRelationship(schema, tableName, columnName, referencedTable, referencedColumn);
                        return; // Found and processed, exit
                    }
                }
            }

            // Check for REFERENCES as separate spec (JSqlParser tokenized format)
            else if (spec.equalsIgnoreCase("REFERENCES") && i + 1 < specs.size()) {
                String referencedTable = cleanIdentifier(specs.get(i + 1));
                String referencedColumn = "Id"; // Default assumption
                
                // Check if column is specified in next spec
                if (i + 2 < specs.size() && specs.get(i + 2).startsWith("(")) {
                    referencedColumn = cleanIdentifier(
                            specs.get(i + 2).replaceAll("[()]", ""));
                }
                
                createForeignKeyRelationship(schema, tableName, columnName, referencedTable, referencedColumn);
                return; // Found and processed, exit
            }
        }
    }

    private static void createForeignKeyRelationship(DatabaseSchema schema, String tableName, String columnName,
                                            String referencedTable, String referencedColumn) {
        RelationshipType relationshipType = determineRelationshipType(schema, tableName, columnName, referencedTable, referencedColumn);
        
        Relationship relationship = Relationship.builder()
                .name("FK_" + tableName + "_" + referencedTable)
                .fromTable(tableName)
                .fromColumn(columnName)
                .toTable(referencedTable)
                .toColumn(referencedColumn)
                .type(relationshipType)
                .build();

        schema.addRelationship(relationship);

        // Update column metadata
        updateColumnForeignKey(schema, tableName, columnName, referencedTable, referencedColumn);
    }

    private static void processForeignKey(String tableName, ForeignKeyIndex fkIndex, DatabaseSchema schema) {
        String fkName = fkIndex.getName() != null ? fkIndex.getName() : "FK_" + tableName;
        net.sf.jsqlparser.schema.Table referencedTable = fkIndex.getTable();
        List<String> fromColumns = fkIndex.getColumnsNames();
        List<String> toColumns = fkIndex.getReferencedColumnNames();

        if (referencedTable != null && fromColumns != null && !fromColumns.isEmpty()) {
            String toTableName = cleanTableName(referencedTable);
            String fromColumn = cleanIdentifier(fromColumns.get(0));
            String toColumn = toColumns != null && !toColumns.isEmpty() ?
                    cleanIdentifier(toColumns.get(0)) : "Id";

            RelationshipType relationshipType = determineRelationshipType(schema, tableName, fromColumn, toTableName, toColumn);

            Relationship relationship = Relationship.builder()
                    .name(fkName)
                    .fromTable(tableName)
                    .fromColumn(fromColumn)
                    .toTable(toTableName)
                    .toColumn(toColumn)
                    .type(relationshipType)
                    .build();

            schema.addRelationship(relationship);

            // Update column metadata
            updateColumnForeignKey(schema, tableName, fromColumn, toTableName, toColumn);
        }
    }

    private static RelationshipType determineRelationshipType(DatabaseSchema schema, String tableName, 
                                                            String columnName, String referencedTable, 
                                                            String referencedColumn) {
        Table fromTable = schema.findTable(tableName);
        Table toTable = schema.findTable(referencedTable);
        
        if (fromTable == null || toTable == null) {
            return RelationshipType.MANY_TO_ONE; // Default fallback
        }

        // Determine the "source side" of the relationship (ONE_* vs MANY_*)
        boolean isSourceUnique = isColumnUniqueInTable(fromTable, columnName);
        
        // Determine the "target side" of the relationship (*_TO_ONE vs *_TO_MANY)  
        boolean isTargetUnique = isColumnUniqueInTable(toTable, referencedColumn);
        
        // Combine source and target constraints to determine relationship type
        if (isSourceUnique && isTargetUnique) {
            return RelationshipType.ONE_TO_ONE;
        } else if (isSourceUnique) {
            return RelationshipType.ONE_TO_MANY;
        } else if (isTargetUnique) {
            return RelationshipType.MANY_TO_ONE;
        } else {
            // Both sides allow duplicates - this would be MANY_TO_MANY but that requires junction table
            // For direct FK relationships, default to MANY_TO_ONE (most common case)
            return RelationshipType.MANY_TO_ONE;
        }
    }

    /**
     * Determines if a column is unique in a table (either PK or has unique constraint)
     */
    private static boolean isColumnUniqueInTable(Table table, String columnName) {
        // Check if column is primary key
        Column column = table.getColumns().stream()
                .filter(c -> c.getName().equalsIgnoreCase(columnName))
                .findFirst().orElse(null);
        
        if (column != null && column.isPrimaryKey()) {
            return true;
        }
        
        // Check if column has unique constraint
        return hasUniqueConstraint(table, columnName);
    }

    private static boolean isJunctionTable(Table table, DatabaseSchema schema) {
        // A pure junction table typically has:
        // 1. A composite primary key made up of exactly the foreign key columns
        // 2. Usually only foreign key columns (no additional data columns)
        // 3. At least 2 foreign key columns
        
        // Check if table has composite primary key
        Index compositePK = table.getIndexes().stream()
                .filter(idx -> idx.getName().startsWith("PK_") && idx.getColumns().size() >= 2)
                .findFirst().orElse(null);
        
        if (compositePK == null) {
            return false;
        }
        
        // Count how many relationships reference this table (indicating foreign keys)
        long relationshipCount = schema.getRelationships().stream()
                .filter(rel -> rel.getFromTable().equals(table.getName()))
                .count();
        
        // Must have at least 2 relationships
        if (relationshipCount < 2) {
            return false;
        }
        
        // For a pure junction table, there should only be as many columns as there are foreign keys
        // Simple heuristic: if table has exactly 2 columns and 2 relationships, likely junction table
        long totalColumnCount = table.getColumns().size();
        
        return totalColumnCount == relationshipCount;
    }

    private static boolean hasUniqueConstraint(Table table, String columnName) {
        // Check if column has a UNIQUE index
        return table.getIndexes().stream()
                .anyMatch(idx -> idx.isUnique() && 
                               idx.getColumns().size() == 1 && 
                               idx.getColumns().get(0).equalsIgnoreCase(columnName));
    }

    private static void createManyToManyRelationships(DatabaseSchema schema) {
        // Find junction tables and create MANY_TO_MANY relationships
        for (Table table : schema.getTables()) {
            if (isJunctionTable(table, schema)) {
                // First, adjust the individual FK relationship types to MANY_TO_ONE for junction tables
                adjustJunctionTableRelationshipTypes(table, schema);
                // Then create the MANY_TO_MANY relationships
                createManyToManyFromJunctionTable(table, schema);
            }
        }
    }

    private static void adjustJunctionTableRelationshipTypes(Table junctionTable, DatabaseSchema schema) {
        // Find all relationships from this junction table and change them to MANY_TO_ONE
        schema.getRelationships().stream()
                .filter(rel -> rel.getFromTable().equals(junctionTable.getName()))
                .forEach(rel -> rel.setType(RelationshipType.MANY_TO_ONE));
    }

    private static void createManyToManyFromJunctionTable(Table junctionTable, DatabaseSchema schema) {
        // Get all foreign key relationships from this junction table
        List<Relationship> junctionRelationships = schema.getRelationships().stream()
                .filter(rel -> rel.getFromTable().equals(junctionTable.getName()))
                .toList();

        // For junction tables, create MANY_TO_MANY relationships between all pairs of referenced tables
        // This correctly handles the case where a junction table connects exactly 2 entities
        for (int i = 0; i < junctionRelationships.size(); i++) {
            for (int j = i + 1; j < junctionRelationships.size(); j++) {
                Relationship rel1 = junctionRelationships.get(i);
                Relationship rel2 = junctionRelationships.get(j);

                // Create bidirectional MANY_TO_MANY relationships between the entity tables
                Relationship manyToMany1 = Relationship.builder()
                        .name("M2M_" + rel1.getToTable() + "_" + rel2.getToTable())
                        .fromTable(rel1.getToTable())
                        .fromColumn(rel1.getToColumn())
                        .toTable(rel2.getToTable())
                        .toColumn(rel2.getToColumn())
                        .type(RelationshipType.MANY_TO_MANY)
                        .build();

                Relationship manyToMany2 = Relationship.builder()
                        .name("M2M_" + rel2.getToTable() + "_" + rel1.getToTable())
                        .fromTable(rel2.getToTable())
                        .fromColumn(rel2.getToColumn())
                        .toTable(rel1.getToTable())
                        .toColumn(rel1.getToColumn())
                        .type(RelationshipType.MANY_TO_MANY)
                        .build();

                schema.addRelationship(manyToMany1);
                schema.addRelationship(manyToMany2);
            }
        }
    }

    private static void updateColumnForeignKey(DatabaseSchema schema, String tableName,
                                        String columnName, String referencedTable,
                                        String referencedColumn) {
        Table table = schema.findTable(tableName);
        if (table != null) {
            table.getColumns().stream()
                    .filter(c -> c.getName().equalsIgnoreCase(columnName))
                    .findFirst()
                    .ifPresent(c -> {
                        c.setForeignKey(true);
                        c.setReferencedTable(referencedTable);
                        c.setReferencedColumn(referencedColumn);
                    });
        }
    }

    private static String cleanTableName(net.sf.jsqlparser.schema.Table table) {
        if (table.getSchemaName() != null) {
            // For tables like [dbo].[Customer], just return Customer
            return cleanIdentifier(table.getName());
        }
        return cleanIdentifier(table.getFullyQualifiedName());
    }

    private static String cleanIdentifier(String identifier) {
        if (identifier == null) return null;

        // Remove SQL Server square brackets and quotes
        return identifier
                .replaceAll("[\\[\\]`\"]", "")
                .replaceAll("^dbo\\.", ""); // Remove dbo schema prefix
    }
}