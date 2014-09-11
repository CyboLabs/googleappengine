// Copyright 2009 Google Inc. All Rights Reserved.
package com.google.appengine.api.datastore;

import com.google.apphosting.datastore.DatastoreV3Pb;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Filter;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Order;
import com.google.apphosting.datastore.DatastoreV3Pb.Query.Order.Direction;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for Entity comparators.
 */
abstract class BaseEntityComparator<EntityT> implements Comparator<EntityT> {

  static final Comparator<Comparable<Object>> MULTI_TYPE_COMPARATOR = new MultiTypeComparator();

  static final Comparator<Order> ORDER_PROPERTY_COMPARATOR = new Comparator<Order>() {
    @Override
    public int compare(Order o1, Order o2) {
      return o1.getProperty().compareTo(o2.getProperty());
    }
  };

  static final Order KEY_ASC_ORDER = new Order().setProperty(Entity.KEY_RESERVED_PROPERTY)
      .setDirection(Order.Direction.ASCENDING);

  final List<Order> orders;
  final Map<String, FilterMatcher> filters;

  BaseEntityComparator(List<Order> orders, List<Filter> filters) {
    this.orders = makeAdjustedOrders(orders, filters);
    this.filters = makeFilterMatchers(orders, filters);
  }

  /**
   * Get a {@link List} with comparable representations of the Entity's
   * values with the given property name.
   *
   * @return A {@link List} of comparable property values, or {@code null} if
   * the entity has no property with the given name.
   */
  abstract List<Comparable<Object>> getComparablePropertyValues(EntityT entity, String property);

  private static List<Order> makeAdjustedOrders(List<Order> orders, List<Filter> filters) {
    List<Order> existsOrders = Lists.newArrayList();
    for (Filter filter : filters) {
      if (filter.getOpEnum() == Filter.Operator.EXISTS) {
        existsOrders.add(new Order()
            .setProperty(filter.getProperty(0).getName())
            .setDirection(Direction.ASCENDING));
      }
    }
    Collections.sort(existsOrders, ORDER_PROPERTY_COMPARATOR);

    List<Order> adjusted = new ArrayList<Order>(orders.size() + existsOrders.size() + 1);
    adjusted.addAll(orders);

    if (adjusted.isEmpty()) {
      for (Filter filter : filters) {
        if (ValidatedQuery.INEQUALITY_OPERATORS.contains(filter.getOpEnum())) {
          adjusted.add(new Order()
            .setProperty(filter.getProperty(0).getName())
            .setDirection(Direction.ASCENDING));
          break;
        }
      }
    }

    adjusted.addAll(existsOrders);

    if (adjusted.isEmpty() || !adjusted.get(adjusted.size() - 1).equals(KEY_ASC_ORDER)) {
      adjusted.add(KEY_ASC_ORDER);
    }
    return adjusted;
  }

  private static Map<String, FilterMatcher> makeFilterMatchers(List<Order> orders,
                                                               List<Filter> filters) {
    Map<String, FilterMatcher> filterMatchers = new HashMap<String, FilterMatcher>();
    for (Filter filter : filters) {
      String name = filter.getProperty(0).getName();
      FilterMatcher filterMatcher = filterMatchers.get(name);
      if (filterMatcher == null) {
        filterMatcher = new FilterMatcher();
        filterMatchers.put(name, filterMatcher);
      }
      filterMatcher.addFilter(filter);
    }

    for (Order order : orders) {
      if (!filterMatchers.containsKey(order.getProperty())) {
        filterMatchers.put(order.getProperty(), FilterMatcher.MATCH_ALL);
      }
      if (order.getProperty().equals(KEY_ASC_ORDER.getProperty())) {
        break;
      }
    }

    return filterMatchers;
  }

  @Override
  public int compare(EntityT entityA, EntityT entityB) {
    int result;

    for (Order order : orders) {
      String property = order.getProperty();

      Collection<Comparable<Object>> aValues = getComparablePropertyValues(entityA, property);
      Collection<Comparable<Object>> bValues = getComparablePropertyValues(entityB, property);

      if (aValues == null || bValues == null) {
        return 0;
      }
      boolean findMin = order.getDirectionEnum() == DatastoreV3Pb.Query.Order.Direction.ASCENDING;
      FilterMatcher matcher = filters.get(property);
      if (matcher == null) {
        matcher = FilterMatcher.MATCH_ALL;
      }
      Comparable<Object> extremeA = multiTypeExtreme(aValues, findMin, matcher);
      Comparable<Object> extremeB = multiTypeExtreme(bValues, findMin, matcher);

      result = MULTI_TYPE_COMPARATOR.compare(extremeA, extremeB);

      if (result != 0) {
        if (order.getDirectionEnum() == DatastoreV3Pb.Query.Order.Direction.DESCENDING) {
          result = -result;
        }
        return result;
      }
    }

    return 0;
  }

  /**
   * Find either the smallest or largest element in a potentially
   * heterogenous collection, depending on the value of {@code findMin}.
   */
  static Comparable<Object> multiTypeExtreme(
      Collection<Comparable<Object>> comparables, boolean findMin, FilterMatcher matcher) {
    boolean findMax = !findMin;
    Comparable<Object> extreme = FilterMatcher.NoValue.INSTANCE;
    for (Comparable<Object> value : comparables) {
      if (!matcher.considerValueForOrder(value)) {
        continue;
      }

      if (extreme == FilterMatcher.NoValue.INSTANCE) {
        extreme = value;
      } else if (findMin && MULTI_TYPE_COMPARATOR.compare(value, extreme) < 0) {
        extreme = value;
      } else if (findMax && MULTI_TYPE_COMPARATOR.compare(value, extreme) > 0) {
        extreme = value;
      }
    }
    if (extreme == FilterMatcher.NoValue.INSTANCE) {
      throw new IllegalArgumentException("Entity contains no relevant values.");
    }
    return extreme;
  }

  private static final class MultiTypeComparator implements Comparator<Comparable<Object>> {
    @Override
    public int compare(Comparable<Object> o1, Comparable<Object> o2) {
      if (o1 == null) {
        if (o2 == null) {
          return 0;
        }
        return -1;
      } else if (o2 == null) {
        return 1;
      }
      int comp1TypeRank = DataTypeTranslator.getTypeRank(o1.getClass());
      int comp2TypeRank = DataTypeTranslator.getTypeRank(o2.getClass());
      if (comp1TypeRank == comp2TypeRank) {
        return o1.compareTo(o2);
      }
      return Integer.compare(comp1TypeRank, comp2TypeRank);
    }
  }
}
