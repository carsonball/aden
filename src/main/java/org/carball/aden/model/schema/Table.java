package org.carball.aden.model.schema;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@RequiredArgsConstructor
public class Table {
    private final String name;
    private List<Column> columns = new ArrayList<>();
    private Column primaryKey;
    private List<Index> indexes = new ArrayList<>();

    public void addColumn(Column column) {
        columns.add(column);
        if (column.isPrimaryKey()) {
            primaryKey = column;
        }
    }

    public void addIndex(Index index) {
        indexes.add(index);
    }
}
