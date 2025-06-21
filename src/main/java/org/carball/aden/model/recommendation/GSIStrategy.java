package org.carball.aden.model.recommendation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GSIStrategy {
    private String indexName;
    private String partitionKey;
    private String sortKey;
    private String purpose;
}