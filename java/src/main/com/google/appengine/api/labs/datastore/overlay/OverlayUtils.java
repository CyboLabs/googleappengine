package com.google.appengine.api.labs.datastore.overlay;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A collection of utility methods and constants for overlay Datastores.
 */
final class OverlayUtils {

  private static final String TOMBSTONE_UUID = "EB4BD565-1C12-494B-9A6D-182BA54D0B3F";

  static final String OVERLAY_NAMESPACE = "_overlay_";

  /**
   * Do not instantiate this class.
   */
  private OverlayUtils() {}

  /**
   * Determines whether an entity is a tombstone.
   */
  static boolean isTombstone(Entity entity) {
    return TOMBSTONE_UUID.equals(entity.getKind());
  }

  /**
   * Given a {@code tombstoneKey}, returns the key where the associated real entity would be stored,
   * if it exists.
   */
  static Key getKeyFromTombstoneKey(Key tombstoneKey) {
    checkNotNull(tombstoneKey);
    return tombstoneKey.getParent();
  }

  /**
   * Creates a tombstone entity corresponding to {@code key}.
   *
   * @param key the key
   * @return a tombstone entity for that key
   */
  static Entity getTombstoneForKey(Key key) {
    checkNotNull(key);
    return new Entity(getTombstoneKey(key));
  }

  /**
   * Returns the tombstone key corresponding to {@code key}.
   */
  static Key getTombstoneKey(Key key) {
    checkNotNull(key);
    return key.getChild(TOMBSTONE_UUID, TOMBSTONE_UUID);
  }

  /**
   * Returns the tombstone keys corresponding to {@code keys}.
   */
  static List<Key> getTombstoneKeys(Iterable<Key> keys) {
    checkNotNull(keys);
    ImmutableList.Builder<Key> newKeys = ImmutableList.<Key>builder();
    for (Key key : keys) {
      newKeys.add(getTombstoneKey(key));
    }
    return newKeys.build();
  }

  /**
   * Returns a list containing both the elements of {@code keys}, and the tombstone keys
   * corresponding to the elements of {@code keys}.
   */
  static List<Key> getKeysAndTombstoneKeys(Iterable<Key> keys) {
    checkNotNull(keys);
    ImmutableList.Builder<Key> newKeys = ImmutableList.<Key>builder();
    for (Key key : keys) {
      newKeys.add(key);
      newKeys.add(getTombstoneKey(key));
    }
    return newKeys.build();
  }

  /**
   * Returns a list containing both the keys of the elements of {@code entities}, and the tombstone
   * keys corresponding to those keys.
   */
  static List<Key> getKeysAndTombstoneKeysForEntities(Iterable<Entity> entities) {
    checkNotNull(entities);
    ImmutableList.Builder<Key> newKeys = ImmutableList.<Key>builder();
    for (Entity entity : entities) {
      Key key = entity.getKey();
      newKeys.add(key);
      newKeys.add(getTombstoneKey(key));
    }
    return newKeys.build();
  }

  /**
   * Creates a list of tombstone entities corresponding to {@code keys}.
   */
  static List<Entity> getTombstonesForKeys(Iterable<Key> keys) {
    checkNotNull(keys);
    ImmutableList.Builder<Entity> tombstones = ImmutableList.<Entity>builder();
    for (Key key : keys) {
      tombstones.add(getTombstoneForKey(key));
    }
    return tombstones.build();
  }

  /**
   * Returns an entity that is the same as {code @entity}, but where the key is replaced by
   * {@code newKey}. This method should only be called if {@code entity} has an incomplete key, and
   * if {@code newKey} has the same kind and parent as {@code entity}.
   */
  static Entity completeKey(Entity entity, Key newKey) {
    checkNotNull(entity);
    checkNotNull(newKey);
    checkArgument(!entity.getKey().isComplete());
    checkArgument(Objects.equals(entity.getParent(), newKey.getParent()));
    checkArgument(Objects.equals(entity.getKind(), newKey.getKind()));
    Entity newEntity = new Entity(newKey);
    newEntity.setPropertiesFrom(entity);
    return newEntity;
  }

  /**
   * Returns a map that indicates how many IDs need to be allocated for each parent-kind combination
   * represented in {@code entities}.
   */
  static Map<IncompleteKey, Integer> getIdsNeededMap(Iterable<Entity> entities) {
    Map<IncompleteKey, Integer> idsNeededMap = Maps.newHashMap();
    for (Entity entity : entities) {
      if (!entity.getKey().isComplete()) {
        IncompleteKey incompleteKey = new IncompleteKey(entity);
        Integer countInteger = idsNeededMap.get(incompleteKey);
        if (countInteger != null) {
          idsNeededMap.put(incompleteKey, countInteger + 1);
        } else {
          idsNeededMap.put(incompleteKey, 1);
        }
      }
    }
    return idsNeededMap;
  }

  static List<Entity> completeEntities(Iterable<Entity> entities, Map<IncompleteKey,
      Iterator<Key>> idsAllocatedMap) {
    List<Entity> completedEntities = Lists.newArrayList();
    for (Entity entity : entities) {
      Entity newEntity = entity;
      if (!entity.getKey().isComplete()) {
        IncompleteKey incompleteKey = new IncompleteKey(entity);
        Iterator<Key> keyIterator = idsAllocatedMap.get(incompleteKey);
        newEntity = OverlayUtils.completeKey(entity, keyIterator.next());
      }
      completedEntities.add(newEntity);
    }
    for (Iterator<Key> iterator : idsAllocatedMap.values()) {
      checkState(!iterator.hasNext());
    }
    return completedEntities;
  }
}
