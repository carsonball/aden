package org.carball.aden.model.schema;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class DatabaseSchema {
    private List<Table> tables = new ArrayList<>();
    private List<Relationship> relationships = new ArrayList<>();

    public void addTable(Table table) {
        tables.add(table);
    }

    public void addRelationship(Relationship relationship) {
        relationships.add(relationship);
    }

    public Table findTable(String name) {
        return tables.stream()
                .filter(t -> t.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}