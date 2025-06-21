package org.carball.aden.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NavigationProperty {
    private String propertyName;
    private String targetEntity;
    private NavigationType type;
}