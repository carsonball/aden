package org.carball.aden.parser;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.table.*;
import net.sf.jsqlparser.statement.create.table.Index;
import org.carball.aden.model.schema.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SchemaParser {

    public DatabaseSchema parseDDL(Path ddlFile) throws IOException {
        String content = Files.readString(ddlFile);
        return parseDDL(content);
    }

    public DatabaseSchema parseDDL(String ddlContent) {
        DatabaseSchema schema = new DatabaseSchema();

        try {
            // Parse all statements in the DDL
            Statements statements = CCJSqlParserUtil.parseStatements(ddlContent);

            // First pass: Create all tables
            for (Statement statement : statements.getStatements()) {
                if (statement instanceof CreateTable) {
                    CreateTable createTable = (CreateTable) statement;
                    org.carball.aden.model.schema.Table table = convertTable(createTable);
                    schema.addTable(table);
                    log.debug("Parsed table: {}", table.getName());
                }
            }

            // Second pass: Extract relationships from foreign keys
            for (Statement statement : statements.getStatements()) {
                if (statement instanceof CreateTable) {
                    CreateTable createTable = (CreateTable) statement;
                    extractRelationships(createTable, schema);
                }
            }

        } catch (JSQLParserException e) {
            log.error("Error parsing DDL: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid SQL DDL: " + e.getMessage(), e);
        }

        return schema;
    }

    private org.carball.aden.model.schema.Table convertTable(CreateTable createTable) {
        String tableName = cleanTableName(createTable.getTable());
        org.carball.aden.model.schema.Table table = new org.carball.aden.model.schema.Table(tableName);

        if (createTable.getColumnDefinitions() != null) {
            for (ColumnDefinition colDef : createTable.getColumnDefinitions()) {
                Column column = convertColumn(colDef);
                table.addColumn(column);
            }
        }

        // Process table constraints (primary keys, etc.)
        if (createTable.getIndexes() != null) {
            for (Index index : createTable.getIndexes()) {
                if ("PRIMARY KEY".equalsIgnoreCase(index.getType())) {
                    processPrimaryKey(index, table);
                } else {
                    processIndex(index, table);
                }
            }
        }

        return table;
    }

    private Column convertColumn(ColumnDefinition colDef) {
        Column.ColumnBuilder builder = Column.builder()
                .name(cleanIdentifier(colDef.getColumnName()))
                .dataType(colDef.getColDataType().getDataType())
                .nullable(true); // Default to nullable

        // Process column specifications
        if (colDef.getColumnSpecs() != null) {
            for (String spec : colDef.getColumnSpecs()) {
                String upperSpec = spec.toUpperCase();

                if (upperSpec.contains("NOT") && upperSpec.contains("NULL")) {
                    builder.nullable(false);
                } else if (upperSpec.equals("PRIMARY") || upperSpec.contains("PRIMARY KEY")) {
                    builder.primaryKey(true);
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

    private void processPrimaryKey(Index index, org.carball.aden.model.schema.Table table) {
        List<String> pkColumns = index.getColumns().stream()
                .map(Index.ColumnParams::getColumnName)
                .map(this::cleanIdentifier)
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
            org.carball.aden.model.schema.Index compositeIndex =
                    org.carball.aden.model.schema.Index.builder()
                            .name("PK_" + table.getName())
                            .columns(pkColumns)
                            .unique(true)
                            .clustered(true)
                            .build();
            table.addIndex(compositeIndex);
        }
    }

    private void processIndex(Index index, org.carball.aden.model.schema.Table table) {
        List<String> indexColumns = index.getColumns().stream()
                .map(Index.ColumnParams::getColumnName)
                .map(this::cleanIdentifier)
                .collect(Collectors.toList());

        org.carball.aden.model.schema.Index tableIndex =
                org.carball.aden.model.schema.Index.builder()
                        .name(index.getName() != null ? index.getName() : "IX_" + String.join("_", indexColumns))
                        .columns(indexColumns)
                        .unique("UNIQUE".equalsIgnoreCase(index.getType()))
                        .clustered(index.getType() != null && index.getType().contains("CLUSTERED"))
                        .build();

        table.addIndex(tableIndex);
    }

    private void extractRelationships(CreateTable createTable, DatabaseSchema schema) {
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
            for (Index index : createTable.getIndexes()) {
                if (index instanceof ForeignKeyIndex) {
                    ForeignKeyIndex fkIndex = (ForeignKeyIndex) index;
                    processForeignKey(tableName, fkIndex, schema);
                }
            }
        }
    }

    private void extractForeignKeyFromSpecs(String tableName, ColumnDefinition colDef, DatabaseSchema schema) {
        String columnName = cleanIdentifier(colDef.getColumnName());

        for (String spec : colDef.getColumnSpecs()) {
            if (spec.toUpperCase().contains("REFERENCES")) {
                // Parse REFERENCES clause
                String[] parts = spec.split("\\s+");
                for (int i = 0; i < parts.length - 1; i++) {
                    if (parts[i].equalsIgnoreCase("REFERENCES")) {
                        String referencedTable = cleanIdentifier(parts[i + 1]);
                        String referencedColumn = "Id"; // Default assumption

                        // Check if column is specified
                        if (i + 2 < parts.length && parts[i + 2].startsWith("(")) {
                            referencedColumn = cleanIdentifier(
                                    parts[i + 2].replaceAll("[()]", ""));
                        }

                        Relationship relationship = Relationship.builder()
                                .name("FK_" + tableName + "_" + referencedTable)
                                .fromTable(tableName)
                                .fromColumn(columnName)
                                .toTable(referencedTable)
                                .toColumn(referencedColumn)
                                .type(RelationshipType.MANY_TO_ONE)
                                .build();

                        schema.addRelationship(relationship);

                        // Update column metadata
                        updateColumnForeignKey(schema, tableName, columnName, referencedTable, referencedColumn);
                        break;
                    }
                }
            }
        }
    }

    private void processForeignKey(String tableName, ForeignKeyIndex fkIndex, DatabaseSchema schema) {
        String fkName = fkIndex.getName() != null ? fkIndex.getName() : "FK_" + tableName;
        Table referencedTable = fkIndex.getTable();
        List<String> fromColumns = fkIndex.getColumnsNames();
        List<String> toColumns = fkIndex.getReferencedColumnNames();

        if (referencedTable != null && fromColumns != null && !fromColumns.isEmpty()) {
            String toTableName = cleanTableName(referencedTable);
            String fromColumn = cleanIdentifier(fromColumns.get(0));
            String toColumn = toColumns != null && !toColumns.isEmpty() ?
                    cleanIdentifier(toColumns.get(0)) : "Id";

            Relationship relationship = Relationship.builder()
                    .name(fkName)
                    .fromTable(tableName)
                    .fromColumn(fromColumn)
                    .toTable(toTableName)
                    .toColumn(toColumn)
                    .type(RelationshipType.MANY_TO_ONE)
                    .build();

            schema.addRelationship(relationship);

            // Update column metadata
            updateColumnForeignKey(schema, tableName, fromColumn, toTableName, toColumn);
        }
    }

    private void updateColumnForeignKey(DatabaseSchema schema, String tableName,
                                        String columnName, String referencedTable,
                                        String referencedColumn) {
        org.carball.aden.model.schema.Table table = schema.findTable(tableName);
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

    private String cleanTableName(Table table) {
        if (table.getSchemaName() != null) {
            // For tables like [dbo].[Customer], just return Customer
            return cleanIdentifier(table.getName());
        }
        return cleanIdentifier(table.getFullyQualifiedName());
    }

    private String cleanIdentifier(String identifier) {
        if (identifier == null) return null;

        // Remove SQL Server square brackets and quotes
        return identifier
                .replaceAll("[\\[\\]`\"]", "")
                .replaceAll("^dbo\\.", ""); // Remove dbo schema prefix
    }
}