package org.carball.aden.model.schema;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class Index {
    private String name;
    private List<String> columns;
    private boolean unique;
    private boolean clustered;
}
