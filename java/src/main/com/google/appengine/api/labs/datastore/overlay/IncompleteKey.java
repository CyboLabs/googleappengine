package com.google.appengine.api.labs.datastore.overlay;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;

import java.util.Objects;

/**
 * A pair of a key parent and a string kind.
 */
final class IncompleteKey {
  final Key parent;
  final String kind;

  public IncompleteKey( Key parent, String kind) {
    this.parent = parent;
    this.kind = checkNotNull(kind);
  }

  public IncompleteKey(Entity entity) {
    checkNotNull(entity);
    this.parent = entity.getParent();
    this.kind = entity.getKind();
  }

  @Override
  public boolean equals( Object object) {
    if (!(object instanceof IncompleteKey)) {
      return false;
    }
    IncompleteKey incompleteKey = (IncompleteKey) object;
    return Objects.equals(parent, incompleteKey.parent)
        && Objects.equals(kind, incompleteKey.kind);
  }

  @Override
  public int hashCode() {
    return Objects.hash(parent, kind);
  }
}
