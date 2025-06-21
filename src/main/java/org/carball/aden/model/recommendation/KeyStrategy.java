package org.carball.aden.model.recommendation;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class KeyStrategy {
    private String attribute;
    private String type;
    private String description;
    private List<String> examples;
}