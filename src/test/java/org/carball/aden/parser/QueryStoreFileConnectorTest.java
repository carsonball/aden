package org.carball.aden.parser;

import org.carball.aden.model.query.QueryStoreQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class QueryStoreFileConnectorTest {

    @TempDir
    Path tempDir;
    
    private Path validExportFile;
    private Path invalidExportFile;
    private Path missingMetadataFile;
    private Path emptyQueriesFile;

    @BeforeEach
    void setUp() throws IOException {
        // Create valid export file
        validExportFile = tempDir.resolve("valid-export.json");
        String validJson = """
            {
              "export_metadata": {
                "database_name": "TestEcommerceApp",
                "export_timestamp": "2025-08-02T10:30:00Z",
                "sql_server_version": "Microsoft SQL Server 2019",
                "query_store_enabled": true,
                "total_queries": 2
              },
              "queries": [
                {
                  "query_id": "1001",
                  "sql_text": "SELECT * FROM Products WHERE CategoryId = @p1",
                  "execution_count": 15420,
                  "avg_duration_ms": 2.45,
                  "avg_cpu_ms": 1.85,
                  "avg_logical_reads": 12.3
                },
                {
                  "query_id": "1002",
                  "sql_text": "SELECT COUNT(*) FROM Orders WHERE CustomerId = @p1",
                  "execution_count": 8950,
                  "avg_duration_ms": 1.23,
                  "avg_cpu_ms": 0.95,
                  "avg_logical_reads": 5.7
                }
              ]
            }
            """;
        Files.writeString(validExportFile, validJson);

        // Create invalid JSON file
        invalidExportFile = tempDir.resolve("invalid-export.json");
        String invalidJson = "{ invalid json structure";
        Files.writeString(invalidExportFile, invalidJson);

        // Create file missing metadata
        missingMetadataFile = tempDir.resolve("missing-metadata.json");
        String missingMetadataJson = """
            {
              "queries": [
                {
                  "query_id": "1001",
                  "sql_text": "SELECT * FROM Products",
                  "execution_count": 100,
                  "avg_duration_ms": 1.0,
                  "avg_cpu_ms": 0.5,
                  "avg_logical_reads": 2.0
                }
              ]
            }
            """;
        Files.writeString(missingMetadataFile, missingMetadataJson);

        // Create file with empty queries array
        emptyQueriesFile = tempDir.resolve("empty-queries.json");
        String emptyQueriesJson = """
            {
              "export_metadata": {
                "database_name": "EmptyDB",
                "export_timestamp": "2025-08-02T10:30:00Z",
                "sql_server_version": "Microsoft SQL Server 2019",
                "query_store_enabled": true,
                "total_queries": 0
              },
              "queries": []
            }
            """;
        Files.writeString(emptyQueriesFile, emptyQueriesJson);
    }

    @Test
    void shouldLoadValidExportFile() throws IOException {
        // Given & When
        QueryStoreFileConnector connector = new QueryStoreFileConnector(validExportFile.toString());

        // Then - should not throw exception
        assertThat(connector).isNotNull();
    }

    @Test
    void shouldReturnCorrectMetadata() throws IOException {
        // Given
        QueryStoreFileConnector connector = new QueryStoreFileConnector(validExportFile.toString());

        // When
        QueryStoreFileConnector.ExportMetadata metadata = connector.getExportMetadata();

        // Then
        assertThat(metadata.databaseName()).isEqualTo("TestEcommerceApp");
        assertThat(metadata.exportTimestamp()).isEqualTo("2025-08-02T10:30:00Z");
        assertThat(metadata.sqlServerVersion()).isEqualTo("Microsoft SQL Server 2019");
        assertThat(metadata.queryStoreEnabled()).isTrue();
        assertThat(metadata.totalQueries()).isEqualTo(2);
    }

    @Test
    void shouldReturnAllQueries() throws IOException {
        // Given
        QueryStoreFileConnector connector = new QueryStoreFileConnector(validExportFile.toString());

        // When
        List<QueryStoreQuery> queries = connector.getAllQueries();

        // Then
        assertThat(queries).hasSize(2);
        
        QueryStoreQuery firstQuery = queries.get(0);
        assertThat(firstQuery.queryId()).isEqualTo("1001");
        assertThat(firstQuery.sqlText()).isEqualTo("SELECT * FROM Products WHERE CategoryId = @p1");
        assertThat(firstQuery.executionCount()).isEqualTo(15420);
        assertThat(firstQuery.avgDurationMs()).isEqualTo(2.45);
        assertThat(firstQuery.avgCpuTimeMs()).isEqualTo(1.85);
        assertThat(firstQuery.avgLogicalReads()).isEqualTo(12.3);

        QueryStoreQuery secondQuery = queries.get(1);
        assertThat(secondQuery.queryId()).isEqualTo("1002");
        assertThat(secondQuery.sqlText()).isEqualTo("SELECT COUNT(*) FROM Orders WHERE CustomerId = @p1");
        assertThat(secondQuery.executionCount()).isEqualTo(8950);
    }

    @Test
    void shouldReturnQueryStoreEnabledStatus() throws IOException {
        // Given
        QueryStoreFileConnector connector = new QueryStoreFileConnector(validExportFile.toString());

        // When
        boolean isEnabled = connector.isQueryStoreEnabled();

        // Then
        assertThat(isEnabled).isTrue();
    }

    @Test
    void shouldReturnCorrectQueryCount() throws IOException {
        // Given
        QueryStoreFileConnector connector = new QueryStoreFileConnector(validExportFile.toString());

        // When
        int queryCount = connector.getQueryStoreQueryCount();

        // Then
        assertThat(queryCount).isEqualTo(2);
    }

    @Test
    void shouldHandleEmptyQueriesFile() throws IOException {
        // Given
        QueryStoreFileConnector connector = new QueryStoreFileConnector(emptyQueriesFile.toString());

        // When
        List<QueryStoreQuery> queries = connector.getAllQueries();

        // Then
        assertThat(queries).isEmpty();
        assertThat(connector.getQueryStoreQueryCount()).isZero();
    }

    @Test
    void shouldThrowExceptionForNonExistentFile() {
        // Given
        String nonExistentFile = tempDir.resolve("does-not-exist.json").toString();

        // When & Then
        assertThatThrownBy(() -> new QueryStoreFileConnector(nonExistentFile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Query Store export file not found");
    }

    @Test
    void shouldThrowExceptionForInvalidJson() {
        // Given & When & Then
        assertThatThrownBy(() -> new QueryStoreFileConnector(invalidExportFile.toString()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldThrowExceptionForMissingMetadata() {
        // Given & When & Then
        assertThatThrownBy(() -> new QueryStoreFileConnector(missingMetadataFile.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing export_metadata section");
    }

    @Test
    void shouldAutoLoadDataWhenCallingMethods() throws IOException {
        // Given
        QueryStoreFileConnector connector = new QueryStoreFileConnector(validExportFile.toString());
        // Note: Not calling loadData() explicitly

        // When & Then - methods should auto-load data
        assertThat(connector.getAllQueries()).hasSize(2);
        assertThat(connector.isQueryStoreEnabled()).isTrue();
        assertThat(connector.getQueryStoreQueryCount()).isEqualTo(2);
    }

    @Test
    void shouldValidateRequiredMetadataFields() throws IOException {
        // Given - create file missing required field
        Path incompleteFile = tempDir.resolve("incomplete-metadata.json");
        String incompleteJson = """
            {
              "export_metadata": {
                "database_name": "TestDB",
                "export_timestamp": "2025-08-02T10:30:00Z"
              },
              "queries": []
            }
            """;
        Files.writeString(incompleteFile, incompleteJson);

        // When & Then
        assertThatThrownBy(() -> new QueryStoreFileConnector(incompleteFile.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required metadata field: query_store_enabled");
    }

    @Test
    void shouldHandleQueryStoreDisabledStatus() throws IOException {
        // Given - create file with Query Store disabled
        Path disabledFile = tempDir.resolve("querystore-disabled.json");
        String disabledJson = """
            {
              "export_metadata": {
                "database_name": "DisabledDB",
                "export_timestamp": "2025-08-02T10:30:00Z",
                "sql_server_version": "Microsoft SQL Server 2019",
                "query_store_enabled": false,
                "total_queries": 0
              },
              "queries": []
            }
            """;
        Files.writeString(disabledFile, disabledJson);

        QueryStoreFileConnector connector = new QueryStoreFileConnector(disabledFile.toString());

        // When
        boolean isEnabled = connector.isQueryStoreEnabled();

        // Then
        assertThat(isEnabled).isFalse();
    }

    @Test
    void shouldFallbackToArrayCountWhenTotalQueriesMissing() throws IOException {
        // Given - create file without total_queries in metadata
        Path fallbackFile = tempDir.resolve("fallback-count.json");
        String fallbackJson = """
            {
              "export_metadata": {
                "database_name": "FallbackDB",
                "export_timestamp": "2025-08-02T10:30:00Z",
                "sql_server_version": "Microsoft SQL Server 2019",
                "query_store_enabled": true
              },
              "queries": [
                {
                  "query_id": "1",
                  "sql_text": "SELECT 1",
                  "execution_count": 1,
                  "avg_duration_ms": 1.0,
                  "avg_cpu_ms": 1.0,
                  "avg_logical_reads": 1.0
                }
              ]
            }
            """;
        Files.writeString(fallbackFile, fallbackJson);

        QueryStoreFileConnector connector = new QueryStoreFileConnector(fallbackFile.toString());

        // When
        int queryCount = connector.getQueryStoreQueryCount();

        // Then
        assertThat(queryCount).isEqualTo(1); // Should count the queries array
    }

    @Test 
    void shouldProvideToStringForExportMetadata() throws IOException {
        // Given
        QueryStoreFileConnector connector = new QueryStoreFileConnector(validExportFile.toString());

        // When
        QueryStoreFileConnector.ExportMetadata metadata = connector.getExportMetadata();
        String toString = metadata.toString();

        // Then
        assertThat(toString).contains("TestEcommerceApp");
        assertThat(toString).contains("2025-08-02T10:30:00Z");
        assertThat(toString).contains("enabled=true");
        assertThat(toString).contains("queries=2");
    }

    @Test
    void shouldHandleMainMethodWithValidFile() {
        // Given
        String[] args = {validExportFile.toString()};

        // When & Then - should not throw exception
        assertThatCode(() -> QueryStoreFileConnector.main(args))
                .doesNotThrowAnyException();
    }

}