package com.google.appengine.api.datastore;

import static com.google.appengine.api.datastore.DataTypeTranslator.getComparablePropertyValue;

import com.google.appengine.api.datastore.Query.SortPredicate;
import com.google.apphosting.datastore.DatastoreV3Pb;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Order;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A comparator with the same ordering as {@link EntityProtoComparators} which uses
 * Entity objects rather than protos.
 */
class EntityComparator extends BaseEntityComparator<Entity> {

  EntityComparator(List<Order> orders) {
    super(orders, Collections.<Filter>emptyList());
  }

  @Override
  List<Comparable<Object>> getComparablePropertyValues(Entity entity, String propertyName) {
    Object prop;
    if (propertyName.equals(Entity.KEY_RESERVED_PROPERTY)) {
      prop = entity.getKey();
    } else if (!entity.hasProperty(propertyName)) {
      return null;
    } else {
      prop = entity.getProperty(propertyName);
    }
    if (prop instanceof Collection<?>) {
      Collection<?> props = (Collection<?>) prop;
      if (props.isEmpty()) {
        return Collections.singletonList(null);
      }
      List<Comparable<Object>> comparableProps = new ArrayList<>(props.size());
      for (Object curProp : props) {
        comparableProps.add(getComparablePropertyValue(curProp));
      }
      return comparableProps;
    } else {
      return Collections.singletonList(getComparablePropertyValue(prop));
    }
  }

  static EntityComparator fromSortPredicates(List<SortPredicate> sortPredicates) {
    List<DatastoreV3Pb.Query.Order> orders = new ArrayList<>(sortPredicates.size());
    for (SortPredicate predicate : sortPredicates) {
      orders.add(QueryTranslator.convertSortPredicateToPb(predicate));
    }
    return new EntityComparator(orders);
  }
}
