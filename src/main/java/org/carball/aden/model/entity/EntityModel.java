package org.carball.aden.model.entity;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@RequiredArgsConstructor
public class EntityModel {
    private final String className;
    private final String fileName;
    private List<NavigationProperty> navigationProperties = new ArrayList<>();
    private List<DataAnnotation> annotations = new ArrayList<>();
    private EntityType type = EntityType.AGGREGATE_ROOT;

    public void addNavigationProperty(NavigationProperty property) {
        navigationProperties.add(property);
    }

    public void addAnnotation(DataAnnotation annotation) {
        annotations.add(annotation);
    }

    public boolean hasCollectionProperty(String propertyName) {
        return navigationProperties.stream()
                .anyMatch(p -> p.getPropertyName().equals(propertyName)
                        && p.getType() == NavigationType.ONE_TO_MANY);
    }

    public boolean hasReferenceProperty(String propertyName) {
        return navigationProperties.stream()
                .anyMatch(p -> p.getPropertyName().equals(propertyName)
                        && p.getType() == NavigationType.MANY_TO_ONE);
    }

    public boolean hasCircularReferences() {
        // Simple check - in real implementation would need graph traversal
        return navigationProperties.stream()
                .anyMatch(p -> p.getTargetEntity().equals(className));
    }
}