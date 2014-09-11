// Copyright 2009 Google Inc. All Rights Reserved.
package com.google.appengine.api.datastore;

import com.google.apphosting.datastore.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Order;
import com.google.storage.onestore.v3.OnestoreEntity;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Utilities for comparing {@link EntityProto}.  This class is
 * only public because the dev appserver needs access to it.  It should not be
 * accessed by user code.
 *
 */
public final class EntityProtoComparators {

  public static final Comparator<Comparable<Object>> MULTI_TYPE_COMPARATOR =
      BaseEntityComparator.MULTI_TYPE_COMPARATOR;

  /**
   * A comparator for {@link com.google.storage.onestore.v3.OnestoreEntity.EntityProto}
   * objects with the same ordering as {@link EntityComparator}.
   */
  public static final class EntityProtoComparator
      extends BaseEntityComparator<OnestoreEntity.EntityProto> {

    public EntityProtoComparator(List<Order> orders) {
      super(orders, Collections.<Filter>emptyList());
    }

    public EntityProtoComparator(List<Order> orders, List<Filter> filters) {
      super(orders, filters);
    }

    public List<Order> getAdjustedOrders() {
      return Collections.unmodifiableList(orders);
    }

    public boolean matches(OnestoreEntity.EntityProto proto) {
      for (String property : filters.keySet()) {
        List<Comparable<Object>> values = getComparablePropertyValues(proto, property);
        if (values == null || !filters.get(property).matches(values)) {
          return false;
        }
      }
      return true;
    }

    public boolean matches(OnestoreEntity.Property prop) {
      FilterMatcher filter = filters.get(prop.getName());
      if (filter == null) {
        return true;
      }
      return filter.matches(Collections.singletonList(
          DataTypeTranslator.getComparablePropertyValue(prop)));
    }

    @Override
    List<Comparable<Object>> getComparablePropertyValues(
        OnestoreEntity.EntityProto entityProto, String propertyName) {
      Collection<OnestoreEntity.Property> entityProperties =
          DataTypeTranslator.findIndexedPropertiesOnPb(entityProto, propertyName);
      if (entityProperties.isEmpty()) {
        return null;
      }
      if (propertyName.equals(Entity.KEY_RESERVED_PROPERTY) && !entityProto.hasKey()) {
        return null;
      }
      List<Comparable<Object>> values = new ArrayList<>(entityProperties.size());
      for (OnestoreEntity.Property prop : entityProperties) {
        values.add(DataTypeTranslator.getComparablePropertyValue(prop));
      }
      return values;
    }
  }

  private EntityProtoComparators() {}
}
