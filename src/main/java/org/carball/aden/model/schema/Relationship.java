package org.carball.aden.model.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Relationship {
    private String name;
    private String fromTable;
    private String fromColumn;
    private String toTable;
    private String toColumn;
    private RelationshipType type;
}
