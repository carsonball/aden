package org.carball.aden.terraform;

import org.carball.aden.model.analysis.NoSQLTarget;
import org.carball.aden.model.recommendation.GSIStrategy;
import org.carball.aden.model.recommendation.KeyStrategy;
import org.carball.aden.model.recommendation.NoSQLRecommendation;
import org.carball.aden.terraform.model.TerraformOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamoDBTerraformGeneratorTest {
    
    private DynamoDBTerraformGenerator generator;
    
    @BeforeEach
    public void setUp() {
        generator = new DynamoDBTerraformGenerator();
    }
    
    @Test
    public void shouldGenerateTerraformForSingleTable() {
        NoSQLRecommendation recommendation = createBasicRecommendation();
        
        TerraformOutput output = generator.generateTerraform(Collections.singletonList(recommendation), "TestApp");
        
        assertThat(output).isNotNull();
        assertThat(output.getMainTf()).contains("resource \"aws_dynamodb_table\" \"orders\"");
        assertThat(output.getMainTf()).contains("hash_key       = \"OrderId\"");
        assertThat(output.getMainTf()).contains("billing_mode   = \"PAY_PER_REQUEST\"");
        assertThat(output.getVariablesTf()).contains("variable \"orders_table_name\"");
        assertThat(output.getOutputsTf()).contains("output \"orders_table_name\"");
        assertThat(output.getOutputsTf()).contains("output \"orders_table_arn\"");
    }
    
    @Test
    public void shouldGenerateTerraformWithSortKey() {
        NoSQLRecommendation recommendation = createRecommendationWithSortKey();
        
        TerraformOutput output = generator.generateTerraform(Collections.singletonList(recommendation), "TestApp");
        
        assertThat(output.getMainTf()).contains("hash_key       = \"UserId\"");
        assertThat(output.getMainTf()).contains("range_key      = \"OrderId\"");
        assertThat(output.getMainTf()).contains("attribute {");
        assertThat(output.getMainTf()).contains("name = \"UserId\"");
        assertThat(output.getMainTf()).contains("name = \"OrderId\"");
    }
    
    @Test
    public void shouldGenerateGlobalSecondaryIndexes() {
        NoSQLRecommendation recommendation = createRecommendationWithGSI();
        
        TerraformOutput output = generator.generateTerraform(Collections.singletonList(recommendation), "TestApp");
        
        assertThat(output.getMainTf()).contains("global_secondary_index {");
        assertThat(output.getMainTf()).contains("name            = \"StatusIndex\"");
        assertThat(output.getMainTf()).contains("hash_key        = \"Status\"");
        assertThat(output.getMainTf()).contains("range_key       = \"CreatedDate\"");
        assertThat(output.getMainTf()).contains("projection_type = \"ALL\"");
    }
    
    @Test
    public void shouldFilterOnlyDynamoDBRecommendations() {
        NoSQLRecommendation dynamoRec = createBasicRecommendation();
        NoSQLRecommendation documentRec = createDocumentDBRecommendation();
        NoSQLRecommendation neptuneRec = createNeptuneRecommendation();
        
        List<NoSQLRecommendation> mixed = Arrays.asList(dynamoRec, documentRec, neptuneRec);
        
        TerraformOutput output = generator.generateTerraform(mixed, "TestApp");
        
        assertThat(output.getMainTf()).contains("orders");
        assertThat(output.getMainTf()).doesNotContain("products");
        assertThat(output.getMainTf()).doesNotContain("customer_graph");
    }
    
    @Test
    public void shouldHandleMultipleTables() {
        NoSQLRecommendation orders = createBasicRecommendation();
        NoSQLRecommendation users = createUsersRecommendation();
        
        TerraformOutput output = generator.generateTerraform(Arrays.asList(orders, users), "TestApp");
        
        assertThat(output.getMainTf()).contains("resource \"aws_dynamodb_table\" \"orders\"");
        assertThat(output.getMainTf()).contains("resource \"aws_dynamodb_table\" \"users\"");
        assertThat(output.getVariablesTf()).contains("variable \"orders_table_name\"");
        assertThat(output.getVariablesTf()).contains("variable \"users_table_name\"");
    }
    
    @Test
    public void shouldMapAttributeTypesCorrectly() {
        NoSQLRecommendation recommendation = createRecommendationWithVariousTypes();
        
        TerraformOutput output = generator.generateTerraform(Collections.singletonList(recommendation), "TestApp");
        
        assertThat(output.getMainTf()).contains("type = \"S\"");
        assertThat(output.getMainTf()).contains("type = \"N\"");
    }
    
    @Test
    public void shouldHandleEmptyRecommendationsList() {
        TerraformOutput output = generator.generateTerraform(Collections.emptyList(), "TestApp");
        
        assertThat(output).isNotNull();
        assertThat(output.isEmpty()).isTrue();
    }
    
    @Test
    public void shouldHandleRecommendationWithNoGSIs() {
        NoSQLRecommendation recommendation = createBasicRecommendation();
        recommendation.setGlobalSecondaryIndexes(null);
        
        TerraformOutput output = generator.generateTerraform(Collections.singletonList(recommendation), "TestApp");
        
        assertThat(output.getMainTf()).doesNotContain("global_secondary_index");
    }
    
    @Test
    public void shouldSanitizeResourceNames() {
        NoSQLRecommendation recommendation = createBasicRecommendation();
        recommendation.setTableName("User-Orders-2024");
        
        TerraformOutput output = generator.generateTerraform(Collections.singletonList(recommendation), "TestApp");
        
        assertThat(output.getMainTf()).contains("resource \"aws_dynamodb_table\" \"user_orders_2024\"");
    }
    
    @Test
    public void shouldIncludeCommonTagsVariable() {
        NoSQLRecommendation recommendation = createBasicRecommendation();
        
        TerraformOutput output = generator.generateTerraform(Collections.singletonList(recommendation), "TestApp");
        
        assertThat(output.getVariablesTf()).contains("variable \"common_tags\"");
        assertThat(output.getVariablesTf()).contains("Environment = \"production\"");
        assertThat(output.getVariablesTf()).contains("ManagedBy   = \"terraform\"");
        assertThat(output.getVariablesTf()).contains("Application = \"TestApp\"");
    }
    
    @Test
    public void shouldUseDefaultApplicationNameWhenNull() {
        NoSQLRecommendation recommendation = createBasicRecommendation();
        
        TerraformOutput output = generator.generateTerraform(Collections.singletonList(recommendation), null);
        
        assertThat(output.getVariablesTf()).contains("Application = \"migrated-from-dotnet\"");
    }
    
    @Test
    public void shouldIncludeProviderConfiguration() {
        NoSQLRecommendation recommendation = createBasicRecommendation();
        
        TerraformOutput output = generator.generateTerraform(Collections.singletonList(recommendation), "TestApp");
        
        assertThat(output.getMainTf()).contains("terraform {");
        assertThat(output.getMainTf()).contains("required_providers");
        assertThat(output.getMainTf()).contains("aws = {");
        assertThat(output.getMainTf()).contains("provider \"aws\"");
    }
    
    private NoSQLRecommendation createBasicRecommendation() {
        NoSQLRecommendation rec = new NoSQLRecommendation();
        rec.setPrimaryEntity("Order");
        rec.setTargetService(NoSQLTarget.DYNAMODB);
        rec.setTableName("Orders");
        rec.setPartitionKey(new KeyStrategy("OrderId", "INT", "Unique order identifier", 
            Arrays.asList("12345", "67890")));
        return rec;
    }
    
    private NoSQLRecommendation createRecommendationWithSortKey() {
        NoSQLRecommendation rec = new NoSQLRecommendation();
        rec.setPrimaryEntity("UserOrder");
        rec.setTargetService(NoSQLTarget.DYNAMODB);
        rec.setTableName("UserOrders");
        rec.setPartitionKey(new KeyStrategy("UserId", "VARCHAR(50)", "User identifier",
                List.of("user123")));
        rec.setSortKey(new KeyStrategy("OrderId", "INT", "Order timestamp",
                List.of("12345")));
        return rec;
    }
    
    private NoSQLRecommendation createRecommendationWithGSI() {
        NoSQLRecommendation rec = createBasicRecommendation();
        
        GSIStrategy gsi = GSIStrategy.builder()
            .indexName("StatusIndex")
            .partitionKey("Status")
            .sortKey("CreatedDate")
            .purpose("Query orders by status")
            .build();
        
        rec.setGlobalSecondaryIndexes(Collections.singletonList(gsi));
        return rec;
    }
    
    private NoSQLRecommendation createDocumentDBRecommendation() {
        NoSQLRecommendation rec = new NoSQLRecommendation();
        rec.setPrimaryEntity("Product");
        rec.setTargetService(NoSQLTarget.DOCUMENTDB);
        rec.setTableName("Products");
        return rec;
    }
    
    private NoSQLRecommendation createNeptuneRecommendation() {
        NoSQLRecommendation rec = new NoSQLRecommendation();
        rec.setPrimaryEntity("CustomerRelationship");
        rec.setTargetService(NoSQLTarget.NEPTUNE);
        rec.setTableName("CustomerGraph");
        return rec;
    }
    
    private NoSQLRecommendation createUsersRecommendation() {
        NoSQLRecommendation rec = new NoSQLRecommendation();
        rec.setPrimaryEntity("User");
        rec.setTargetService(NoSQLTarget.DYNAMODB);
        rec.setTableName("Users");
        rec.setPartitionKey(new KeyStrategy("UserId", "VARCHAR(50)", "User identifier",
                List.of("user123")));
        return rec;
    }
    
    private NoSQLRecommendation createRecommendationWithVariousTypes() {
        NoSQLRecommendation rec = new NoSQLRecommendation();
        rec.setPrimaryEntity("MixedTypes");
        rec.setTargetService(NoSQLTarget.DYNAMODB);
        rec.setTableName("MixedTypes");
        rec.setPartitionKey(new KeyStrategy("StringId", "VARCHAR(100)", "String key", null));
        rec.setSortKey(new KeyStrategy("NumberValue", "DECIMAL(10,2)", "Numeric sort key", null));
        return rec;
    }
}