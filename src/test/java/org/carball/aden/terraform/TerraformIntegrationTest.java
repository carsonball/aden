package org.carball.aden.terraform;

import org.carball.aden.model.analysis.NoSQLTarget;
import org.carball.aden.model.recommendation.GSIStrategy;
import org.carball.aden.model.recommendation.KeyStrategy;
import org.carball.aden.model.recommendation.NoSQLRecommendation;
import org.carball.aden.terraform.model.TerraformOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TerraformIntegrationTest {
    
    private DynamoDBTerraformGenerator generator;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    public void setUp() {
        generator = new DynamoDBTerraformGenerator();
    }
    
    @Test
    public void shouldGenerateCompleteEcommerceTerraformFiles() throws IOException {
        List<NoSQLRecommendation> recommendations = createEcommerceRecommendations();
        
        TerraformOutput output = generator.generateTerraform(recommendations, "TestEcommerceApp");
        
        assertThat(output).isNotNull();
        assertThat(output.isEmpty()).isFalse();
        
        Path mainTfPath = tempDir.resolve("main.tf");
        Path variablesTfPath = tempDir.resolve("variables.tf");
        Path outputsTfPath = tempDir.resolve("outputs.tf");
        
        Files.writeString(mainTfPath, output.getMainTf());
        Files.writeString(variablesTfPath, output.getVariablesTf());
        Files.writeString(outputsTfPath, output.getOutputsTf());
        
        assertThat(mainTfPath).exists();
        assertThat(variablesTfPath).exists();
        assertThat(outputsTfPath).exists();
        
        String mainContent = Files.readString(mainTfPath);
        assertThat(mainContent).contains("resource \"aws_dynamodb_table\" \"customerorders\"");
        assertThat(mainContent).contains("resource \"aws_dynamodb_table\" \"products\"");
        assertThat(mainContent).contains("resource \"aws_dynamodb_table\" \"orderitems\"");
        
        String variablesContent = Files.readString(variablesTfPath);
        assertThat(variablesContent).contains("variable \"customerorders_table_name\"");
        assertThat(variablesContent).contains("variable \"products_table_name\"");
        assertThat(variablesContent).contains("variable \"orderitems_table_name\"");
        
        String outputsContent = Files.readString(outputsTfPath);
        assertThat(outputsContent).contains("output \"customerorders_table_arn\"");
        assertThat(outputsContent).contains("output \"products_table_arn\"");
        assertThat(outputsContent).contains("output \"orderitems_table_arn\"");
    }
    
    @Test
    public void shouldGenerateValidHCLSyntax() {
        List<NoSQLRecommendation> recommendations = createEcommerceRecommendations();
        
        TerraformOutput output = generator.generateTerraform(recommendations, "TestEcommerceApp");
        
        String mainTf = output.getMainTf();
        assertThat(countOccurrences(mainTf, "{")).isEqualTo(countOccurrences(mainTf, "}"));
        
        assertThat(mainTf).doesNotContain("null");
        assertThat(mainTf).doesNotContain("undefined");
        
        assertThat(mainTf).containsPattern("resource\\s+\"aws_dynamodb_table\"\\s+\"\\w+\"\\s+\\{");
        assertThat(mainTf).containsPattern("hash_key\\s+=\\s+\"\\w+\"");
    }
    
    @Test
    public void shouldHandleComplexGSIConfigurations() {
        NoSQLRecommendation recommendation = createComplexGSIRecommendation();
        
        TerraformOutput output = generator.generateTerraform(List.of(recommendation), "TestEcommerceApp");
        
        String mainTf = output.getMainTf();
        assertThat(mainTf).contains("global_secondary_index {");
        assertThat(countOccurrences(mainTf, "global_secondary_index {")).isEqualTo(3);
        assertThat(mainTf).contains("StatusDateIndex");
        assertThat(mainTf).contains("UserOrderIndex");
        assertThat(mainTf).contains("ProductCategoryIndex");
    }
    
    @Test
    public void shouldGenerateProperAttributeDefinitions() {
        List<NoSQLRecommendation> recommendations = createEcommerceRecommendations();
        
        TerraformOutput output = generator.generateTerraform(recommendations, "TestEcommerceApp");
        
        String mainTf = output.getMainTf();
        
        assertThat(mainTf).contains("attribute {");
        assertThat(mainTf).containsPattern("name\\s+=\\s+\"CustomerId\"");
        assertThat(mainTf).containsPattern("type\\s+=\\s+\"S\"");
        
        int attributeCount = countOccurrences(mainTf, "attribute {");
        assertThat(attributeCount).isGreaterThan(0);
        
        assertThat(mainTf).doesNotContain("attribute {\n  }");
    }
    
    @Test
    public void shouldIncludeTagsInAllResources() {
        List<NoSQLRecommendation> recommendations = createEcommerceRecommendations();
        
        TerraformOutput output = generator.generateTerraform(recommendations, "TestEcommerceApp");
        
        String mainTf = output.getMainTf();
        int resourceCount = countOccurrences(mainTf, "resource \"aws_dynamodb_table\"");
        int tagsCount = countOccurrences(mainTf, "tags = merge(");
        
        assertThat(tagsCount).isEqualTo(resourceCount);
        assertThat(mainTf).contains("Entity =");
    }
    
    private List<NoSQLRecommendation> createEcommerceRecommendations() {
        NoSQLRecommendation customerOrders = new NoSQLRecommendation();
        customerOrders.setPrimaryEntity("Order");
        customerOrders.setTargetService(NoSQLTarget.DYNAMODB);
        customerOrders.setTableName("CustomerOrders");
        customerOrders.setPartitionKey(new KeyStrategy("CustomerId", "VARCHAR(50)", "Customer identifier", null));
        customerOrders.setSortKey(new KeyStrategy("OrderId", "INT", "Order identifier", null));
        
        GSIStrategy statusIndex = GSIStrategy.builder()
            .indexName("StatusIndex")
            .partitionKey("OrderStatus")
            .sortKey("OrderDate")
            .purpose("Query orders by status")
            .build();
        customerOrders.setGlobalSecondaryIndexes(Collections.singletonList(statusIndex));
        
        NoSQLRecommendation products = new NoSQLRecommendation();
        products.setPrimaryEntity("Product");
        products.setTargetService(NoSQLTarget.DYNAMODB);
        products.setTableName("Products");
        products.setPartitionKey(new KeyStrategy("ProductId", "INT", "Product identifier", null));
        
        GSIStrategy categoryIndex = GSIStrategy.builder()
            .indexName("CategoryIndex")
            .partitionKey("Category")
            .sortKey("ProductName")
            .purpose("Query products by category")
            .build();
        products.setGlobalSecondaryIndexes(Collections.singletonList(categoryIndex));
        
        NoSQLRecommendation orderItems = new NoSQLRecommendation();
        orderItems.setPrimaryEntity("OrderItem");
        orderItems.setTargetService(NoSQLTarget.DYNAMODB);
        orderItems.setTableName("OrderItems");
        orderItems.setPartitionKey(new KeyStrategy("OrderId", "INT", "Order identifier", null));
        orderItems.setSortKey(new KeyStrategy("ProductId", "INT", "Product identifier", null));
        
        return Arrays.asList(customerOrders, products, orderItems);
    }
    
    private NoSQLRecommendation createComplexGSIRecommendation() {
        NoSQLRecommendation rec = new NoSQLRecommendation();
        rec.setPrimaryEntity("ComplexOrder");
        rec.setTargetService(NoSQLTarget.DYNAMODB);
        rec.setTableName("ComplexOrders");
        rec.setPartitionKey(new KeyStrategy("OrderId", "INT", "Order identifier", null));
        rec.setSortKey(new KeyStrategy("ItemId", "INT", "Item identifier", null));
        
        GSIStrategy gsi1 = GSIStrategy.builder()
            .indexName("StatusDateIndex")
            .partitionKey("Status")
            .sortKey("CreatedDate")
            .purpose("Query by status and date")
            .build();
        
        GSIStrategy gsi2 = GSIStrategy.builder()
            .indexName("UserOrderIndex")
            .partitionKey("UserId")
            .sortKey("OrderDate")
            .purpose("Query user orders")
            .build();
        
        GSIStrategy gsi3 = GSIStrategy.builder()
            .indexName("ProductCategoryIndex")
            .partitionKey("ProductCategory")
            .sortKey("Price")
            .purpose("Query by category and price")
            .build();
        
        rec.setGlobalSecondaryIndexes(Arrays.asList(gsi1, gsi2, gsi3));
        return rec;
    }
    
    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}