// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.appengine.api.datastore;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.DataTypeTranslator.Type;
import com.google.appengine.api.users.User;
import com.google.apphosting.datastore.EntityV4;
import com.google.storage.onestore.v3.OnestoreEntity.PropertyValue;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;

/**
 * A raw datastore value.
 *
 * These are returned by projection queries when a {@link PropertyProjection} does not
 * specify a type.
 *
 * @see Query#getProjections()
 */
public final class RawValue implements Serializable {
  private static final long serialVersionUID = 8176992854378814427L;
  private transient PropertyValue valueV3 = null;
  private transient EntityV4.Value valueV4 = null;

  RawValue(PropertyValue propertyValue) {
    this.valueV3 = checkNotNull(propertyValue);
  }

  RawValue(EntityV4.Value propertyValue) {
    this.valueV4 = checkNotNull(propertyValue);
  }

  /**
   * Returns an object of the exact type passed in.
   *
   * @param type the class object for the desired type
   * @return an object of type T or {@code null}
   * @throws IllegalArgumentException if the raw value cannot be converted into the given type
   */
  @SuppressWarnings("unchecked")
  public <T> T asStrictType(Class<T> type) {
    Object value = asType(type);
    if (value != null) {
      checkArgument(type.isAssignableFrom(value.getClass()), "Unsupported type: " + type);
    }
    return (T) value;
  }

  /**
   * Returns the object normally returned by the datastore if given type is passed in.
   *
   * All integer values are returned as {@link Long}.
   * All floating point values are returned as {@link Double}.
   *
   * @param type the class object for the desired type
   * @return an object of type T or {@code null}
   * @throws IllegalArgumentException if the raw value cannot be converted into the given type
   */
  public Object asType(Class<?> type) {
    Type<?> typeAdapter = DataTypeTranslator.getTypeMap().get(type);
    checkArgument(typeAdapter != null, "Unsupported type: " + type);

    if (valueV3 != null) {
      if (typeAdapter.hasValue(valueV3)) {
        return typeAdapter.getValue(valueV3);
      }
    } else if (valueV4 != null) {
      if (typeAdapter.hasValue(valueV4)) {
        return typeAdapter.getValue(valueV4);
      }
    }

    checkArgument(getValue() == null, "Type mismatch.");
    return null;
  }

  /**
   * Returns the raw value.
   *
   * @return An object of type {@link Boolean}, {@link Double}, {@link GeoPt}, {@link Key},
   * {@code byte[]}, {@link User} or {@code null}.
   */
  public Object getValue() {
    if (valueV3 != null) {
      if (valueV3.hasBooleanValue()) {
        return valueV3.isBooleanValue();
      } else if (valueV3.hasDoubleValue()) {
        return valueV3.getDoubleValue();
      } else if (valueV3.hasInt64Value()) {
        return valueV3.getInt64Value();
      } else if (valueV3.hasPointValue()) {
        return asType(GeoPt.class);
      } else if (valueV3.hasReferenceValue()) {
        return asType(Key.class);
      } else if (valueV3.hasStringValue()) {
        return valueV3.getStringValueAsBytes();
      } else if (valueV3.hasUserValue()) {
        return asType(User.class);
      }
    } else if (valueV4 != null) {
      if (valueV4.hasBooleanValue()) {
        return valueV4.getBooleanValue();
      } else if (valueV4.hasDoubleValue()) {
        return valueV4.getDoubleValue();
      } else if (valueV4.hasIntegerValue()) {
        return valueV4.getIntegerValue();
      } else if (valueV4.hasEntityValue()) {
        if (valueV4.getMeaning() == 20) {
          return asType(User.class);
        }
        return asType(GeoPt.class);
      } else if (valueV4.hasKeyValue()) {
        return asType(Key.class);
      } else if (valueV4.hasStringValue()) {
        return valueV4.getStringValueBytes().toByteArray();
      } else if (valueV4.hasBlobKeyValue()) {
        return asType(BlobKey.class);
      } else if (valueV4.hasBlobValue()) {
        return valueV4.getBlobValue().toByteArray();
      } else if (valueV4.hasTimestampMicrosecondsValue()) {
        return valueV4.getTimestampMicrosecondsValue();
      } else if (valueV4.hasGeoPointValue()) {
        return asType(GeoPt.class);
      }
    }
    return null;
  }

  private void writeObject(java.io.ObjectOutputStream out) throws IOException {
    if (valueV3 != null) {
      out.write(1);
      valueV3.writeTo(out);
    } else {
      out.write(2);
      valueV4.writeTo(out);
    }
    out.defaultWriteObject();
  }

  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
    int version = in.read();
    if (version == 1) {
      valueV3 = new PropertyValue();
      valueV3.parseFrom(in);
    } else if (version == 2) {
      valueV4 = EntityV4.Value.PARSER.parseFrom(in);
    } else {
      checkArgument(false, "unknown RawValue format");
    }
    in.defaultReadObject();
  }

  @Override
  public int hashCode() {
    Object value = getValue();
    return value == null ? 0 : value.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    RawValue other = (RawValue) obj;
    Object value = getValue();
    Object otherValue = other.getValue();
    if (value != null) {
      if (otherValue != null) {
        if (value instanceof byte[]) {
          if (otherValue instanceof byte[]) {
            return Arrays.equals((byte[]) value, (byte[]) otherValue);
          }
        } else {
          return value.equals(otherValue);
        }
      }
    } else if (otherValue == null) {
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return "RawValue [value=" + getValue() + "]";
  }
}
