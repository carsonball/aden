package org.carball.aden.model.schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Column {
    private String name;
    private String dataType;
    private boolean nullable;
    private boolean primaryKey;
    private boolean foreignKey;
    private String defaultValue;
    private String referencedTable;
    private String referencedColumn;
}