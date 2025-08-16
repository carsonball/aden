package org.carball.aden.terraform;

import org.carball.aden.model.analysis.NoSQLTarget;
import org.carball.aden.model.recommendation.GSIStrategy;
import org.carball.aden.model.recommendation.NoSQLRecommendation;
import org.carball.aden.terraform.model.TerraformOutput;

import java.util.*;
import java.util.stream.Collectors;

public class DynamoDBTerraformGenerator {
    
    public TerraformOutput generateTerraform(List<NoSQLRecommendation> recommendations, String projectName) {
        List<NoSQLRecommendation> dynamoDbRecommendations = filterDynamoDbRecommendations(recommendations);
        
        if (dynamoDbRecommendations.isEmpty()) {
            return new TerraformOutput("", "", "");
        }
        
        StringBuilder mainTf = new StringBuilder();
        mainTf.append(TerraformTemplate.PROVIDER_CONFIG);
        mainTf.append("\n");
        
        for (NoSQLRecommendation rec : dynamoDbRecommendations) {
            mainTf.append(generateTableResource(rec));
            mainTf.append("\n");
        }
        
        String variablesTf = generateVariables(dynamoDbRecommendations, projectName);
        String outputsTf = generateOutputs(dynamoDbRecommendations);
        
        return new TerraformOutput(mainTf.toString(), variablesTf, outputsTf);
    }
    
    private List<NoSQLRecommendation> filterDynamoDbRecommendations(List<NoSQLRecommendation> recommendations) {
        return recommendations.stream()
                .filter(rec -> rec.getTargetService() == NoSQLTarget.DYNAMODB)
                .collect(Collectors.toList());
    }
    
    private String generateTableResource(NoSQLRecommendation rec) {
        String resourceName = sanitizeResourceName(rec.getTableName());
        
        String rangeKey = "";
        if (rec.getSortKey() != null) {
            rangeKey = String.format("  range_key      = \"%s\"", rec.getSortKey().getAttribute());
        }
        
        String attributes = generateAttributes(rec);
        
        String gsis = "";
        if (rec.getGlobalSecondaryIndexes() != null && !rec.getGlobalSecondaryIndexes().isEmpty()) {
            gsis = generateGSIs(rec.getGlobalSecondaryIndexes());
        }
        
        return String.format(TerraformTemplate.TABLE_RESOURCE_TEMPLATE,
                resourceName,  // resource name
                resourceName,  // var.X_table_name
                rec.getPartitionKey() != null ? rec.getPartitionKey().getAttribute() : "",  // hash_key
                rangeKey,      // range_key (optional)
                attributes,    // attribute blocks
                gsis,          // GSI blocks
                resourceName,  // var.X_table_name for tags
                rec.getPrimaryEntity()  // Entity tag value
        );
    }
    
    private String generateAttributes(NoSQLRecommendation rec) {
        Set<String> attributesNeeded = new HashSet<>();
        Map<String, String> attributeTypes = new HashMap<>();
        
        if (rec.getPartitionKey() != null) {
            attributesNeeded.add(rec.getPartitionKey().getAttribute());
            attributeTypes.put(rec.getPartitionKey().getAttribute(), 
                mapAttributeType(rec.getPartitionKey().getType()));
        }
        
        if (rec.getSortKey() != null) {
            attributesNeeded.add(rec.getSortKey().getAttribute());
            attributeTypes.put(rec.getSortKey().getAttribute(), 
                mapAttributeType(rec.getSortKey().getType()));
        }
        
        if (rec.getGlobalSecondaryIndexes() != null) {
            for (GSIStrategy gsi : rec.getGlobalSecondaryIndexes()) {
                if (gsi.getPartitionKey() != null) {
                    attributesNeeded.add(gsi.getPartitionKey());
                    attributeTypes.putIfAbsent(gsi.getPartitionKey(), "S");
                }
                if (gsi.getSortKey() != null) {
                    attributesNeeded.add(gsi.getSortKey());
                    attributeTypes.putIfAbsent(gsi.getSortKey(), "S");
                }
            }
        }
        
        StringBuilder attributes = new StringBuilder();
        for (String attr : attributesNeeded) {
            attributes.append(String.format(TerraformTemplate.ATTRIBUTE_TEMPLATE, 
                    attr, attributeTypes.get(attr)));
        }
        
        return attributes.toString();
    }
    
    private String generateGSIs(List<GSIStrategy> gsis) {
        StringBuilder gsiConfig = new StringBuilder();
        
        for (GSIStrategy gsi : gsis) {
            String rangeKey = "";
            if (gsi.getSortKey() != null && !gsi.getSortKey().isEmpty()) {
                rangeKey = String.format("    range_key       = \"%s\"", gsi.getSortKey());
            }
            
            gsiConfig.append(String.format(TerraformTemplate.GSI_TEMPLATE,
                    gsi.getIndexName(),
                    gsi.getPartitionKey(),
                    rangeKey,
                    "ALL"
            ));
        }
        
        return gsiConfig.toString();
    }
    
    private String generateVariables(List<NoSQLRecommendation> recommendations, String projectName) {
        StringBuilder variables = new StringBuilder();
        
        // Add common tags variable
        String applicationName = projectName != null ? projectName : "migrated-from-dotnet";
        variables.append(String.format(TerraformTemplate.VARIABLE_COMMON_TAGS_TEMPLATE, applicationName));
        variables.append("\n");
        
        // Add table name variables
        for (NoSQLRecommendation rec : recommendations) {
            String resourceName = sanitizeResourceName(rec.getTableName());
            variables.append(String.format(TerraformTemplate.VARIABLE_TABLE_NAME_TEMPLATE,
                    resourceName,
                    rec.getPrimaryEntity(),
                    rec.getTableName()
            ));
            variables.append("\n");
        }
        
        return variables.toString();
    }
    
    private String generateOutputs(List<NoSQLRecommendation> recommendations) {
        StringBuilder outputs = new StringBuilder();
        
        for (NoSQLRecommendation rec : recommendations) {
            String resourceName = sanitizeResourceName(rec.getTableName());
            
            // Add table name output
            outputs.append(String.format(TerraformTemplate.OUTPUT_TABLE_NAME_TEMPLATE,
                    resourceName,
                    rec.getPrimaryEntity(),
                    resourceName
            ));
            outputs.append("\n");
            
            // Add table ARN output
            outputs.append(String.format(TerraformTemplate.OUTPUT_TABLE_ARN_TEMPLATE,
                    resourceName,
                    rec.getPrimaryEntity(),
                    resourceName
            ));
            outputs.append("\n");
        }
        
        return outputs.toString();
    }
    
    private String sanitizeResourceName(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("^[0-9]", "_$0")
                .replaceAll("_{2,}", "_");
    }
    
    private String mapAttributeType(String type) {
        if (type == null) return "S";
        
        String upperType = type.toUpperCase();
        if (upperType.contains("STRING") || upperType.contains("VARCHAR") || 
            upperType.contains("TEXT") || upperType.contains("CHAR")) {
            return "S";
        } else if (upperType.contains("INT") || upperType.contains("DECIMAL") || 
                   upperType.contains("NUMERIC") || upperType.contains("FLOAT") || 
                   upperType.contains("DOUBLE") || upperType.contains("MONEY")) {
            return "N";
        } else if (upperType.contains("BINARY") || upperType.contains("BLOB") || 
                   upperType.contains("IMAGE")) {
            return "B";
        }
        return "S";
    }
}