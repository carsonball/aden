package org.carball.aden.model.analysis;

import lombok.Data;
import org.carball.aden.model.entity.EntityModel;
import org.carball.aden.model.query.QueryPattern;
import org.carball.aden.model.query.QueryType;
import org.carball.aden.model.schema.RelationshipType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class EntityUsageProfile {
    private final EntityModel entity;
    private int eagerLoadingCount = 0;
    private int readCount = 0;
    private int writeCount = 0;
    private List<String> alwaysLoadedWithEntities = new ArrayList<>();
    private List<QueryPattern> queryPatterns = new ArrayList<>();
    private Map<String, RelationshipType> relatedEntities = new HashMap<>();

    public EntityUsageProfile(EntityModel entity) {
        this.entity = entity;
    }

    public void addQueryPattern(QueryPattern pattern) {
        queryPatterns.add(pattern);
    }

    public void incrementEagerLoadingCount(int count) {
        eagerLoadingCount += count;
    }

    public double getReadToWriteRatio() {
        return writeCount == 0 ? readCount : (double) readCount / writeCount;
    }

    public String getEntityName() {
        return entity.getClassName();
    }

    public boolean hasSimpleKeyBasedAccess() {
        // Simplified logic - check if most queries are single entity lookups
        return queryPatterns.stream()
                .filter(p -> p.getQueryType() == QueryType.SINGLE_ENTITY)
                .count() > queryPatterns.size() * 0.7;
    }

    public boolean hasComplexRelationships() {
        return entity.getNavigationProperties().size() > 3 ||
                entity.hasCircularReferences();
    }
    
    public void addRelatedEntity(String entityName, RelationshipType type) {
        relatedEntities.put(entityName, type);
    }
}