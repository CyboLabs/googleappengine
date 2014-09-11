// Copyright 2007 Google Inc. All rights reserved.

package com.google.appengine.api.datastore;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.Entity.UnindexedValue;
import com.google.appengine.api.users.User;
import com.google.apphosting.datastore.EntityV4;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.io.protocol.ProtocolSupport;
import com.google.protobuf.ByteString;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.OnestoreEntity.Path;
import com.google.storage.onestore.v3.OnestoreEntity.Path.Element;
import com.google.storage.onestore.v3.OnestoreEntity.Property;
import com.google.storage.onestore.v3.OnestoreEntity.Property.Meaning;
import com.google.storage.onestore.v3.OnestoreEntity.PropertyValue;
import com.google.storage.onestore.v3.OnestoreEntity.PropertyValue.ReferenceValue;
import com.google.storage.onestore.v3.OnestoreEntity.PropertyValue.ReferenceValuePathElement;
import com.google.storage.onestore.v3.OnestoreEntity.PropertyValue.UserValue;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code DataTypeTranslator} is a utility class for converting
 * between the data store's {@code Property} protocol buffers and the
 * user-facing classes ({@code String}, {@code User}, etc.).
 *
 */
public final class DataTypeTranslator  {

  private static final RawValueType RAW_VALUE_TYPE = new RawValueType();
  /**
   * The list of supported types.
   *
   * Note: If you're going to modify this list, also update
   * DataTypeUtils. We're not building {@link DataTypeUtils#getSupportedTypes}
   * directly from this typeMap, because we want {@link DataTypeUtils} to be
   * translatable by GWT, so that {@link Entity Entities} can be easily sent
   * via GWT RPC.  Also, if you add a type here that is not immutable you'll
   * need to add special handling for it in {@link Entity#clone()}.
   */
  private static final Map<Class<?>, Type<?>> typeMap = Maps.newHashMap();
  static {
    typeMap.put(RawValue.class, RAW_VALUE_TYPE);

    typeMap.put(Float.class, new DoubleType());
    typeMap.put(Double.class, new DoubleType());

    typeMap.put(Byte.class, new Int64Type());
    typeMap.put(Short.class, new Int64Type());
    typeMap.put(Integer.class, new Int64Type());
    typeMap.put(Long.class, new Int64Type());
    typeMap.put(Date.class, new DateType());
    typeMap.put(Rating.class, new RatingType());

    typeMap.put(String.class, new StringType());
    typeMap.put(Link.class, new LinkType());
    typeMap.put(ShortBlob.class, new ShortBlobType());
    typeMap.put(Category.class, new CategoryType());
    typeMap.put(PhoneNumber.class, new PhoneNumberType());
    typeMap.put(PostalAddress.class, new PostalAddressType());
    typeMap.put(Email.class, new EmailType());
    typeMap.put(IMHandle.class, new IMHandleType());
    typeMap.put(BlobKey.class, new BlobKeyType());
    typeMap.put(Blob.class, new BlobType());
    typeMap.put(Text.class, new TextType());
    typeMap.put(EmbeddedEntity.class, new EmbeddedEntityType());

    typeMap.put(Boolean.class, new BoolType());
    typeMap.put(User.class, new UserType());
    typeMap.put(Key.class, new KeyType());
    typeMap.put(GeoPt.class, new GeoPtType());

    assert typeMap.keySet().equals(DataTypeUtils.getSupportedTypes())
        : "Warning:  DataTypeUtils and DataTypeTranslator do not agree "
        + "about supported classes: " + typeMap.keySet() + " vs. "
        + DataTypeUtils.getSupportedTypes();
  }

  /**
   * A map with the {@link Comparable} classes returned by all the instances of
   * {@link Type#asComparable(Object)} as keys and the pb code point as the value.
   * Used for comparing values that don't map to the same pb code point.
   */
  private static final
  Map<Class<? extends Comparable<?>>, Integer> comparableTypeMap =
      new HashMap<Class<? extends Comparable<?>>, Integer>();

  static {
    comparableTypeMap.put(ComparableByteArray.class, PropertyValue.kstringValue);
    comparableTypeMap.put(Long.class, PropertyValue.kint64Value);
    comparableTypeMap.put(Double.class, PropertyValue.kdoubleValue);
    comparableTypeMap.put(Boolean.class, PropertyValue.kbooleanValue);
    comparableTypeMap.put(User.class, PropertyValue.kUserValueGroup);
    comparableTypeMap.put(Key.class, PropertyValue.kReferenceValueGroup);
    comparableTypeMap.put(GeoPt.class, PropertyValue.kPointValueGroup);
  }

  /**
   * Add all of the properties in the specified map to an {@code EntityProto}.
   * This involves determining the type of each property and creating the
   * proper type-specific protocol buffer.
   *
   * If the property value is an {@link UnindexedValue}, or if it's a
   * type that is never indexed, e.g. {@code Text} and {@code Blob}, it's
   * added to {@code EntityProto.raw_property}. Otherwise it's added to
   * {@code EntityProto.property}.
   *
   * @param map A not {@code null} map of all the properties which will
   * be set on {@code proto}
   * @param proto A not {@code null} protocol buffer
   */
  public static void addPropertiesToPb(Map<String, Object> map, EntityProto proto) {
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      String name = entry.getKey();
      boolean indexed = !(entry.getValue() instanceof UnindexedValue);
      Object value = PropertyContainer.unwrapValue(entry.getValue());

      if (value instanceof Collection<?>) {
        Collection<?> values = (Collection<?>) value;
        if (values.isEmpty()) {
          addPropertyToPb(name, null, indexed, false, proto);
        } else {
          for (Object listValue : values) {
            addPropertyToPb(name, listValue, indexed, true, proto);
          }
        }
      } else {
        addPropertyToPb(name, value, indexed, false, proto);
      }
    }
  }

  /**
   * Adds a property to {@code entity}.
   *
   * @param name the property name
   * @param value the property value
   * @param indexed whether this property should be indexed. This may be
   * overriden by property types like Blob and Text that are never indexed.
   * @param multiple whether this property has multiple values
   * @param entity the entity to populate
   */
  private static void addPropertyToPb(String name, Object value, boolean indexed, boolean multiple,
      EntityProto entity) {
    Property property = new Property();
    property.setName(name);
    property.setMultiple(multiple);
    PropertyValue newValue = property.getMutableValue();
    if (value != null) {
      Type<?> type = getType(value.getClass());
      Meaning meaning = type.getV3Meaning();
      if (meaning != property.getMeaningEnum()) {
        property.setMeaning(meaning);
      }
      indexed &= type.toV3Value(value, newValue);
    }
    if (!indexed) {
      entity.addRawProperty(property);
    } else {
      entity.addProperty(property);
    }
  }

  static PropertyValue toV3Value(Object value) {
    PropertyValue propertyValue = new PropertyValue();
    if (value != null) {
      getType(value.getClass()).toV3Value(value, propertyValue);
    }
    return propertyValue;
  }

  /**
   * Copy all of the indexed properties present on {@code proto} into {@code map}.
   */
  public static void extractIndexedPropertiesFromPb(EntityProto proto, Map<String, Object> map) {
    for (Property property : proto.propertys()) {
      addPropertyToMap(property, true, map);
    }
  }

  /**
   * Copy all of the unindexed properties present on {@code proto} into {@code map}.
   */
  private static void extractUnindexedPropertiesFromPb(EntityProto proto, Map<String, Object> map) {
    for (Property property : proto.rawPropertys()) {
      addPropertyToMap(property, false, map);
    }
  }

  /**
   * Copy all of the properties present on {@code proto} into {@code map}.
   */
  public static void extractPropertiesFromPb(EntityProto proto, Map<String, Object> map) {
    extractIndexedPropertiesFromPb(proto, map);
    extractUnindexedPropertiesFromPb(proto, map);
  }

  /**
   * Copy all of the implicit properties present on {@code proto} into {@code map}.
   */
  public static void extractImplicitPropertiesFromPb(EntityProto proto, Map<String, Object> map) {
    for (Property property : getImplicitProperties(proto)) {
      addPropertyToMap(property, true, map);
    }
  }

  private static Iterable<Property> getImplicitProperties(EntityProto proto) {
    return Collections.singleton(buildImplicitKeyProperty(proto));
  }

  private static Property buildImplicitKeyProperty(EntityProto proto) {
    Property keyProp = new Property();
    keyProp.setName(Entity.KEY_RESERVED_PROPERTY);
    PropertyValue propVal = new PropertyValue();
    propVal.setReferenceValue(KeyType.toReferenceValue(proto.getKey()));
    keyProp.setValue(propVal);
    return keyProp;
  }

  /**
   * Locates and returns all indexed properties with the given name on the
   * given proto. If there are a mix of matching multiple and non-multiple
   * properties, the collection will contain the first non-multiple property.
   * @return A list, potentially empty, containing matching properties.
   */
  public static Collection<Property> findIndexedPropertiesOnPb(
      EntityProto proto, String propertyName) {
    if (propertyName.equals(Entity.KEY_RESERVED_PROPERTY)) {
      return Collections.singleton(buildImplicitKeyProperty(proto));
    }
    List<Property> matchingMultipleProps = new ArrayList<>();
    for (Property prop : proto.propertys()) {
      if (prop.getName().equals(propertyName)) {
        if (!prop.isMultiple()) {
          return Collections.singleton(prop);
        } else {
          matchingMultipleProps.add(prop);
        }
      }
    }
    return matchingMultipleProps;
  }

  private static void addPropertyToMap(Property property, boolean indexed,
      Map<String, Object> map) {
    String name = property.getName();
    Object value = getPropertyValue(property);

    if (property.isMultiple()) {
      @SuppressWarnings({"unchecked"})
      List<Object> results = (List<Object>) PropertyContainer.unwrapValue(map.get(name));
      if (results == null) {
        results = new ArrayList<Object>();
        map.put(name, indexed ? results : new UnindexedValue(results));
      }
      results.add(value);
    } else {
      map.put(name, indexed ? value : new UnindexedValue(value));
    }
  }

  /**
   * Returns the value for the property as its canonical type.
   *
   * @param property a not {@code null} property
   * @return {@code null} if no value was set for {@code property}
   */
  public static Object getPropertyValue(Property property) {
    PropertyValue value = property.getValue();
    for (Type<?> type : typeMap.values()) {
      if (type.isType(property.getMeaningEnum(), value)) {
        return type.getValue(value);
      }
    }
    return null;
  }

  /**
   * @see #addPropertiesToPb(Map, EntityProto)
   */
  static void addPropertiesToPb(Map<String, Object> map, EntityV4.Entity.Builder proto) {
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      proto.addProperty(toV4Property(entry.getKey(), entry.getValue()));
    }
  }

  /**
   * Copy all of the properties present on {@code proto} into {@code map}.
   *
   * EntityV4 must know if the proto came from an index-only query as User and GeoPt types
   * overwrite the INDEX_ONLY meaning.
   *
   * @param proto the proto from which to extract properties
   * @param indexOnly if the proto is from an index only query (a projection query)
   * @param map the map to populate
   */
  static void extractPropertiesFromPb(EntityV4.EntityOrBuilder proto, boolean indexOnly,
      Map<String, Object> map) {
    if (indexOnly) {
      for (EntityV4.PropertyOrBuilder prop : proto.getPropertyOrBuilderList()) {
        map.put(prop.getName(), new RawValue(prop.getValue()));
      }
    } else {
      for (EntityV4.PropertyOrBuilder prop : proto.getPropertyOrBuilderList()) {
        addPropertyToMap(prop, map);
      }
    }
  }

  static EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
    if (value == null) {
      EntityV4.Value.Builder builder = EntityV4.Value.newBuilder();
      setIndexed(indexed, builder);
      return builder;
    }
    return getType(value.getClass()).toV4Value(value, indexed);
  }

  private static EntityV4.Property.Builder toV4Property(String name, Object value) {
    EntityV4.Property.Builder builder = EntityV4.Property.newBuilder();
    builder.setName(name);
    boolean indexed = !(value instanceof UnindexedValue);
    value = PropertyContainer.unwrapValue(value);
    if (value instanceof Collection<?>) {
      Collection<?> values = (Collection<?>) value;
      if (values.isEmpty()) {
        builder.setValue(toV4Value(null, indexed));
      } else {
        EntityV4.Value.Builder valueBuilder = builder.getValueBuilder();
        for (Object listValue : values) {
          valueBuilder.addListValue(toV4Value(listValue, indexed));
        }
      }
    } else {
      builder.setValue(toV4Value(value, indexed));
    }
    return builder;
  }

  private static void addPropertyToMap(EntityV4.PropertyOrBuilder prop, Map<String, Object> map) {
    EntityV4.ValueOrBuilder value = prop.getValueOrBuilder();
    boolean indexed = value.getIndexed();
    Object result;
    if (value.getListValueCount() > 0) {
      ArrayList<Object> resultList = new ArrayList<Object>(value.getListValueCount());
      for (EntityV4.ValueOrBuilder subValue : value.getListValueOrBuilderList()) {
        indexed &= subValue.getIndexed();
        if (subValue.getListValueCount() > 0) {
          throw new IllegalArgumentException("Invalid Entity PB: list within a list.");
        }
        resultList.add(getValue(subValue));
      }
      result = resultList;
    } else {
      result = getValue(value);
    }

    if (!indexed) {
      result = new UnindexedValue(result);
    }
    map.put(prop.getName(), result);
  }

  private static Object getValue(EntityV4.ValueOrBuilder value) {
    for (Type<?> type : typeMap.values()) {
      if (type.isType(value)) {
        return type.getValue(value);
      }
    }
    return null;
  }

  private static Meaning getV3MeaningOf(EntityV4.ValueOrBuilder value) {
    return Meaning.valueOf(value.getMeaning());
  }

  private static AppIdNamespace toAppIdNamespace(EntityV4.PartitionIdOrBuilder partitionId) {
    return new AppIdNamespace(partitionId.getDatasetId(),
        partitionId.hasNamespace() ? partitionId.getNamespace() : "");
  }

  private static EntityV4.PartitionId.Builder toV4PartitionId(AppIdNamespace appNs) {
    EntityV4.PartitionId.Builder builder = EntityV4.PartitionId.newBuilder();
    builder.setDatasetId(appNs.getAppId());
    if (!appNs.getNamespace().isEmpty()) {
      builder.setNamespace(appNs.getNamespace());
    }
    return builder;
  }

  static EntityV4.Key.Builder toV4Key(Key key) {
    EntityV4.Key.Builder builder = EntityV4.Key.newBuilder();
    builder.setPartitionId(toV4PartitionId(key.getAppIdNamespace()));
    List<EntityV4.Key.PathElement> pathElementList = new ArrayList<>();
    do {
      EntityV4.Key.PathElement.Builder pathElement = EntityV4.Key.PathElement.newBuilder();
      pathElement.setKind(key.getKind());
      if (key.getName() != null) {
        pathElement.setName(key.getName());
      } else if (key.getId() != Key.NOT_ASSIGNED) {
        pathElement.setId(key.getId());
      }
      pathElementList.add(pathElement.build());
      key = key.getParent();
    } while (key != null);
    builder.addAllPathElement(Lists.reverse(pathElementList));
    return builder;
  }

  static Key toKey(EntityV4.KeyOrBuilder proto) {
    AppIdNamespace appIdNamespace = toAppIdNamespace(proto.getPartitionId());
    if (proto.getPathElementCount() == 0) {
      throw new IllegalArgumentException("Invalid Key PB: no elements.");
    }
    Key key = null;
    for (EntityV4.Key.PathElementOrBuilder e : proto.getPathElementOrBuilderList()) {
      String kind = e.getKind();
      if (e.hasName() && e.hasId()) {
        throw new IllegalArgumentException("Invalid Key PB: both id and name are set.");
      }
      key = new Key(kind, key, e.getId(), e.hasName() ? e.getName() : null, appIdNamespace);
    }
    return key;
  }

  static Entity toEntity(EntityV4.EntityOrBuilder v4Entity) {
    Entity entity = new Entity(DataTypeTranslator.toKey(v4Entity.getKey()));
    DataTypeTranslator.extractPropertiesFromPb(v4Entity, false,
        entity.getPropertyMap());
    return entity;
  }

  static Entity toEntity(EntityV4.EntityOrBuilder v4Entity, Collection<Projection> projections) {
    Entity entity = new Entity(DataTypeTranslator.toKey(v4Entity.getKey()));

    Map<String, Object> values = Maps.newHashMap();
    DataTypeTranslator.extractPropertiesFromPb(v4Entity, true, values);
    for (Projection projection : projections) {
      entity.setProperty(projection.getName(), projection.getValue(values));
    }
    return entity;
  }

  static EntityV4.Entity.Builder toV4Entity(Entity entity) {
    EntityV4.Entity.Builder v4Entity = EntityV4.Entity.newBuilder();
    v4Entity.setKey(toV4Key(entity.getKey()));
    addPropertiesToPb(entity.getPropertyMap(), v4Entity);
    return v4Entity;
  }

  /**
   * Returns the value for the property as its comparable representation type.
   *
   * @param property a not {@code null} property
   * @return {@code null} if no value was set for {@code property}
   */
  @SuppressWarnings("unchecked")
  public static Comparable<Object> getComparablePropertyValue(Property property) {
    return (Comparable<Object>) RAW_VALUE_TYPE.asComparable(new RawValue(property.getValue()));
  }

  /**
   * Converts the given {@link Object} into a supported value then returns it as
   * a comparable object so it can be compared to other data types.
   *
   * @param value any Object that can be converted into a supported DataType
   * @return {@code null} if value is null
   * @throws UnsupportedOperationException if value is not supported
   */
  @SuppressWarnings("unchecked")
  static Comparable<Object> getComparablePropertyValue(Object value) {
    return value == null ? null
        : (Comparable<Object>) getType(value.getClass()).asComparable(value);
  }

  /**
   * Get the rank of the given datastore type relative to other datastore
   * types.  Note that datastore types do not necessarily have unique ranks.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static int getTypeRank(Class<? extends Comparable> datastoreType) {
    return comparableTypeMap.get(datastoreType);
  }

  /**
   * Gets the {@link Type} that knows how to translate objects of
   * type {@code clazz} into protocol buffers that the data store can
   * handle.
   * @throws UnsupportedOperationException if clazz is not supported
   */
  @SuppressWarnings("unchecked")
  private static <T> Type<T> getType(Class<T> clazz) {
    if (typeMap.containsKey(clazz)) {
      return (Type<T>) typeMap.get(clazz);
    } else {
      throw new UnsupportedOperationException("Unsupported data type: " + clazz.getName());
    }
  }

  /**
   * {@code Type} is an abstract class that knows how to convert Java
   * objects of one or more types into datastore representations.
   *
   * @param <T> The canonical Java class for this type.
   */
  abstract static class Type<T> {
    /**
     * @returns {@code true} if the given meaning and property value matches this {@link Type}.
     */
    public final boolean isType(Meaning meaning, PropertyValue propertyValue) {
      return meaning == getV3Meaning() && hasValue(propertyValue);
    }

    /**
     * @returns {@code true} if the given value matches this {@link Type}.
     */
    public boolean isType(EntityV4.ValueOrBuilder propertyValue) {
      return getV3MeaningOf(propertyValue) == getV3Meaning() && hasValue(propertyValue);
    }

    /**
     * Returns the {@link Comparable} for the given value, or
     * {@code null} if values of this type are not comparable.
     */
    public abstract Comparable<?> asComparable(Object value);

    /**
     * Sets the value of {@code propertyValue} to {@code value}.
     * @returns if the value is indexable
     */
    public abstract boolean toV3Value(Object value, PropertyValue propertyValue);

    /**
     * Returns a new V4 Value for the given parameters.
     *
     * @param value the Java native value to convert
     * @param indexed the desired indexing, ignored for types that are not indexable
     * @returns the EntityV4 representation of the given value and desired indexing
     */
    public abstract EntityV4.Value.Builder toV4Value(Object value, boolean indexed);

    /**
     * Returns the value of {@code propertyValue} as its canonical Java type.
     *
     * Use {@link #isType} first to determine if the property has a value of the given type.
     *
     * @param propertyValue a not {@code null} value representing this {@code Type}
     * @return the canonical Java representation of {@code propertyValue}.
     */
    public abstract T getValue(PropertyValue propertyValue);

    /**
     * @see Type#getValue(PropertyValue)
     */
    public abstract T getValue(EntityV4.ValueOrBuilder propertyValue);

    /**
     * @returns {@code true} if a value of this {@code Type} is set on the given propertyValue.
     */
    public abstract boolean hasValue(PropertyValue propertyValue);

    /**
     * @returns {@code true} if a value of this {@code Type} is set on the given propertyValue.
     */
    public abstract boolean hasValue(EntityV4.ValueOrBuilder propertyValue);

    /**
     * @returns the {@link Meaning} for this {@link Type}
     */
    protected Meaning getV3Meaning() {
      return Meaning.NO_MEANING;
    }
  }

  /**
   * A base class with common functions for types that have the same datastore representation.
   *
   * @param <S> the datastore type
   * @param <T> the canonical Java class for this type
   */
  private abstract static class BaseVariantType<S, T> extends Type<T> {
    /**
     * @returns the datastore representation of the given value
     */
    protected abstract S toDatastoreValue(Object value);
    /**
     * @returns the native representation of the given value
     */
    protected abstract T fromDatastoreValue(S datastoreValue);
  }

  /**
   * Base class for types that store strings in the datastore.
   *
   * @param <T> the canonical Java class for this type
   */
  private abstract static class BaseStringType<T> extends BaseVariantType<String, T> {
    @Override
    public boolean toV3Value(Object value, PropertyValue propertyValue) {
      propertyValue.setStringValue(toDatastoreValue(value));
      return true;
    }

    @Override
    public EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
      EntityV4.Value.Builder builder = EntityV4.Value.newBuilder();
      builder.setStringValue(toDatastoreValue(value));
      setIndexed(indexed, builder);
      setMeaning(getV3Meaning().getValue(), builder);
      return builder;
    }

    @Override
    public final T getValue(PropertyValue propertyValue) {
      return fromDatastoreValue(propertyValue.getStringValue());
    }

    @Override
    public T getValue(EntityV4.ValueOrBuilder propertyValue) {
      return fromDatastoreValue(propertyValue.getStringValue());
    }

    @Override
    public final boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasStringValue();
    }

    @Override
    public boolean hasValue(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.hasStringValue();
    }

    @Override
    public ComparableByteArray asComparable(Object value) {
      return new ComparableByteArray(ProtocolSupport.toBytesUtf8(toDatastoreValue(value)));
    }
  }

  /**
   * Base class for types that store bytes in the datastore.
   *
   * @param <T> the canonical Java class for this type
   */
  private abstract static class BaseBlobType<T> extends BaseVariantType<byte[], T> {
    protected abstract boolean isIndexable();

    @Override
    public final boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasStringValue();
    }

    @Override
    public final boolean toV3Value(Object value, PropertyValue propertyValue) {
      propertyValue.setStringValueAsBytes(toDatastoreValue(value));
      return isIndexable();
    }

    @Override
    public EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
      EntityV4.Value.Builder builder = EntityV4.Value.newBuilder();
      builder.setBlobValue(ByteString.copyFrom(toDatastoreValue(value)));
      setIndexed(indexed && isIndexable(), builder);
      return builder;
    }

    @Override
    public final T getValue(PropertyValue propertyValue) {
      return fromDatastoreValue(propertyValue.getStringValueAsBytes());
    }

    @Override
    public final ComparableByteArray asComparable(Object value) {
      return isIndexable() ? new ComparableByteArray(toDatastoreValue(value)) : null;
    }
  }

  /**
   * Base class for types that store predefined entities in V4.
   *
   * @param <T> the canonical Java class for this type
   */
  private abstract static class BasePredefinedEntityType<T> extends Type<T> {
    /**
     * @returns the predefined entity meaning to use in V4
     */
    protected abstract int getV4Meaning();

    /**
     * @returns the V4 Entity representation for the given value
     */
    protected abstract EntityV4.Entity getEntity(Object value);

    @Override
    public final boolean isType(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.getMeaning() == getV4Meaning() && hasValue(propertyValue);
    }

    @Override
    public final boolean hasValue(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.hasEntityValue();
    }

    @Override
    public final EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
      EntityV4.Value.Builder builder = EntityV4.Value.newBuilder();
      builder.setEntityValue(getEntity(value));
      setIndexed(indexed, builder);
      setMeaning(getV4Meaning(), builder);
      return builder;
    }
  }

  /**
   * Returns the V4 property representation for the given name and value, unindexed.
   */
  private static EntityV4.Property makeUnindexedProperty(String name, double value) {
    return EntityV4.Property.newBuilder()
        .setName(name)
        .setValue(EntityV4.Value.newBuilder().setDoubleValue(value).setIndexed(false))
        .build();
  }

  /**
   * Returns the V4 property representation for the given name and value, unindexed.
   */
  private static EntityV4.Property makeUnindexedProperty(String name, String value) {
    return EntityV4.Property.newBuilder()
        .setName(name)
        .setValue(EntityV4.Value.newBuilder().setStringValue(value).setIndexed(false))
        .build();
  }

  /**
   * Base class for types that int64 values in the datastore.
   *
   * @param <T> the canonical Java class for this type
   */
  private abstract static class BaseInt64Type<T> extends BaseVariantType<Long, T> {
    @Override
    public final boolean toV3Value(Object value, PropertyValue propertyValue) {
      propertyValue.setInt64Value(toDatastoreValue(value));
      return true;
    }

    @Override
    public EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
      EntityV4.Value.Builder builder = EntityV4.Value.newBuilder();
      builder.setIntegerValue(toDatastoreValue(value));
      setIndexed(indexed, builder);
      setMeaning(getV3Meaning().getValue(), builder);
      return builder;
    }

    @Override
    public T getValue(PropertyValue propertyValue) {
      return fromDatastoreValue(propertyValue.getInt64Value());
    }

    @Override
    public T getValue(EntityV4.ValueOrBuilder propertyValue) {
      return fromDatastoreValue(propertyValue.getIntegerValue());
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasInt64Value();
    }

    @Override
    public boolean hasValue(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.hasIntegerValue();
    }

    @Override
    public Long asComparable(Object value) {
      return toDatastoreValue(value);
    }
  }

  /**
   * The type for projected index values.
   */
  private static final class RawValueType extends Type<RawValue> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.INDEX_VALUE;
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return true;
    }

    @Override
    public boolean hasValue(EntityV4.ValueOrBuilder propertyValue) {
      return true;
    }

    @Override
    public boolean toV3Value(Object value, PropertyValue propertyValue) {
      throw new UnsupportedOperationException();
    }

    @Override
    public EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
      throw new UnsupportedOperationException();
    }

    @Override
    public RawValue getValue(PropertyValue propertyValue) {
      return new RawValue(propertyValue);
    }

    @Override
    public RawValue getValue(EntityV4.ValueOrBuilder propertyValue) {
      EntityV4.Value value = null;
      if (propertyValue instanceof EntityV4.Value) {
        value = (EntityV4.Value) propertyValue;
      } else {
        value = ((EntityV4.Value.Builder) propertyValue).build();
      }
      return new RawValue(value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Comparable<?> asComparable(Object value) {
      value = ((RawValue) value).getValue();
      if (value instanceof byte[]) {
        return new ComparableByteArray((byte[]) value);
      }
      return (Comparable<?>) value;
    }
  }

  /**
   * The raw String type.
   */
  private static final class StringType extends BaseStringType<String> {
    @Override
    protected String toDatastoreValue(Object value) {
      return value.toString();
    }

    @Override
    protected String fromDatastoreValue(String datastoreValue) {
      return datastoreValue;
    }
  }

  /**
   * The raw int64 type.
   */
  private static final class Int64Type extends BaseInt64Type<Long> {
    @Override
    protected Long toDatastoreValue(Object value) {
      return ((Number) value).longValue();
    }

    @Override
    protected Long fromDatastoreValue(Long datastoreValue) {
      return datastoreValue;
    }
  }

  /**
   * The raw double type.
   */
  private static final class DoubleType extends Type<Double> {
    @Override
    public boolean toV3Value(Object value, PropertyValue propertyValue) {
      propertyValue.setDoubleValue(((Number) value).doubleValue());
      return true;
    }

    @Override
    public EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
      EntityV4.Value.Builder builder = EntityV4.Value.newBuilder();
      builder.setDoubleValue(((Number) value).doubleValue());
      setIndexed(indexed, builder);
      return builder;
    }

    @Override
    public Double getValue(PropertyValue propertyValue) {
      return propertyValue.getDoubleValue();
    }

    @Override
    public Double getValue(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.getDoubleValue();
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasDoubleValue();
    }

    @Override
    public boolean hasValue(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.hasDoubleValue();
    }

    @Override
    public Double asComparable(Object value) {
      return ((Number) value).doubleValue();
    }
  }

  /**
   * The raw boolean type.
   */
  private static final class BoolType extends Type<Boolean> {
    @Override
    public boolean toV3Value(Object value, PropertyValue propertyValue) {
      propertyValue.setBooleanValue((Boolean) value);
      return true;
    }

    @Override
    public EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
      EntityV4.Value.Builder builder = EntityV4.Value.newBuilder();
      builder.setBooleanValue((Boolean) value);
      setIndexed(indexed, builder);
      return builder;
    }

    @Override
    public Boolean getValue(PropertyValue propertyValue) {
      return propertyValue.isBooleanValue();
    }

    @Override
    public Boolean getValue(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.getBooleanValue();
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasBooleanValue();
    }

    @Override
    public boolean hasValue(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.hasBooleanValue();
    }

    @Override
    public Boolean asComparable(Object value) {
      return (Boolean) value;
    }
  }

  /**
   * The user type.
   *
   * Stored as an entity with a special meaning in V4.
   */
  private static final class UserType extends BasePredefinedEntityType<User> {
    public static final int MEANING_PREDEFINED_ENTITY_USER = 20;
    public static final String PROPERTY_NAME_EMAIL = "email";
    public static final String PROPERTY_NAME_AUTH_DOMAIN = "auth_domain";
    public static final String PROPERTY_NAME_USER_ID = "user_id";

    @Override
    public int getV4Meaning() {
      return MEANING_PREDEFINED_ENTITY_USER;
    }

    @Override
    public EntityV4.Entity getEntity(Object value) {
      User user = (User) value;
      EntityV4.Entity.Builder builder = EntityV4.Entity.newBuilder();
      builder.addProperty(makeUnindexedProperty(PROPERTY_NAME_EMAIL, user.getEmail()));
      builder.addProperty(makeUnindexedProperty(PROPERTY_NAME_AUTH_DOMAIN, user.getAuthDomain()));
      if (user.getUserId() != null) {
        builder.addProperty(makeUnindexedProperty(PROPERTY_NAME_USER_ID, user.getUserId()));
      }
      return builder.build();
    }

    @Override
    public boolean toV3Value(Object value, PropertyValue propertyValue) {
      User user = (User) value;
      UserValue userValue = new UserValue();
      userValue.setEmail(user.getEmail());
      userValue.setAuthDomain(user.getAuthDomain());
      if (user.getUserId() != null) {
        userValue.setObfuscatedGaiaid(user.getUserId());
      }
      userValue.setGaiaid(0);
      propertyValue.setUserValue(userValue);
      return true;
    }

    @Override
    public User getValue(PropertyValue propertyValue) {
      UserValue userValue = propertyValue.getUserValue();
      String userId = userValue.hasObfuscatedGaiaid() ? userValue.getObfuscatedGaiaid() : null;
      return new User(userValue.getEmail(), userValue.getAuthDomain(), userId);
    }

    @Override
    public User getValue(EntityV4.ValueOrBuilder propertyValue) {
      String email = "";
      String authDomain = "";
      String userId = null;
      for (EntityV4.PropertyOrBuilder prop :
          propertyValue.getEntityValueOrBuilder().getPropertyOrBuilderList()) {
        if (prop.getName().equals(PROPERTY_NAME_EMAIL)) {
          email = prop.getValueOrBuilder().getStringValue();
        } else if (prop.getName().equals(PROPERTY_NAME_AUTH_DOMAIN)) {
          authDomain = prop.getValueOrBuilder().getStringValue();
        } else if (prop.getName().equals(PROPERTY_NAME_USER_ID)) {
          userId = prop.getValueOrBuilder().getStringValue();
        }
      }
      return new User(email, authDomain, userId);
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasUserValue();
    }

    @Override
    public final Comparable<User> asComparable(Object value) {
      return (User) value;
    }
  }

  /**
   * The GeoPt type.
   *
   * Stored as a GeoPoint value with no meaning in V4.
   *
   * TODO(user): The above comment isn't completely accurate until we have switched the
   * internal format to V1BETA3.
   */
  private static class GeoPtType extends Type<GeoPt> {
    public static final int MEANING_PREDEFINED_ENTITY_GEORSS_POINT = 9;
    public static final String PROPERTY_NAME_X = "x";
    public static final String PROPERTY_NAME_Y = "y";

    @Override
    public boolean isType(EntityV4.ValueOrBuilder propertyValue) {
      if (propertyValue.hasGeoPointValue() && !propertyValue.hasMeaning()) {
        return true;
      } else if (propertyValue.hasEntityValue()
          && propertyValue.getMeaning() == MEANING_PREDEFINED_ENTITY_GEORSS_POINT) {
        return true;
      } else {
        return false;
      }
    }

    @Override
    public boolean toV3Value(Object value, PropertyValue propertyValue) {
      GeoPt geoPt = (GeoPt) value;
      PropertyValue.PointValue pv = new PropertyValue.PointValue()
          .setX(geoPt.getLatitude())
          .setY(geoPt.getLongitude());
      propertyValue.setPointValue(pv);
      return true;
    }

    @Override
    public final EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
      GeoPt geoPt = (GeoPt) value;
      EntityV4.Value.Builder builder = EntityV4.Value.newBuilder();
      builder.getEntityValueBuilder()
          .addProperty(makeUnindexedProperty(PROPERTY_NAME_X, geoPt.getLatitude()))
          .addProperty(makeUnindexedProperty(PROPERTY_NAME_Y, geoPt.getLongitude()))
          .build();
      setIndexed(indexed, builder);
      setMeaning(MEANING_PREDEFINED_ENTITY_GEORSS_POINT, builder);
      return builder;
    }

    @Override
    public GeoPt getValue(PropertyValue propertyValue) {
      PropertyValue.PointValue pv = propertyValue.getPointValue();
      return new GeoPt((float) pv.getX(), (float) pv.getY());
    }

    @Override
    public GeoPt getValue(EntityV4.ValueOrBuilder propertyValue) {
      double x = 0;
      double y = 0;
      for (EntityV4.PropertyOrBuilder prop :
          propertyValue.getEntityValueOrBuilder().getPropertyOrBuilderList()) {
        if (prop.getName().equals(PROPERTY_NAME_X)) {
          x = prop.getValueOrBuilder().getDoubleValue();
        } else if (prop.getName().equals(PROPERTY_NAME_Y)) {
          y = prop.getValueOrBuilder().getDoubleValue();
        }
      }
      if (propertyValue.hasGeoPointValue()) {
        x = propertyValue.getGeoPointValue().getLatitude();
        y = propertyValue.getGeoPointValue().getLongitude();
      }
      return new GeoPt((float) x, (float) y);
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasPointValue();
    }

    @Override
    public final boolean hasValue(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.hasGeoPointValue() || propertyValue.hasEntityValue();
    }

    @Override
    public Meaning getV3Meaning() {
      return Meaning.GEORSS_POINT;
    }

    @Override
    public final Comparable<GeoPt> asComparable(Object value) {
      return (GeoPt) value;
    }
  }

  /**
   * The key/reference type.
   */
  private static final class KeyType extends Type<Key> {
    @Override
    public boolean toV3Value(Object value, PropertyValue propertyValue) {
      Reference keyRef = KeyTranslator.convertToPb((Key) value);
      propertyValue.setReferenceValue(toReferenceValue(keyRef));
      return true;
    }

    @Override
    public EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
      EntityV4.Value.Builder builder = EntityV4.Value.newBuilder();
      builder.setKeyValue(toV4Key((Key) value));
      setIndexed(indexed, builder);
      return builder;
    }

    @Override
    public Key getValue(PropertyValue propertyValue) {
      return KeyTranslator.createFromPb(toReference(propertyValue.getReferenceValue()));
    }

    @Override
    public Key getValue(EntityV4.ValueOrBuilder propertyValue) {
      return toKey(propertyValue.getKeyValue());
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasReferenceValue();
    }

    @Override
    public boolean hasValue(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.hasKeyValue();
    }

    @Override
    public Key asComparable(Object value) {
      return (Key) value;
    }

    private static ReferenceValue toReferenceValue(Reference keyRef) {
      ReferenceValue refValue = new ReferenceValue();
      refValue.setApp(keyRef.getApp());
      if (keyRef.hasNameSpace()) {
        refValue.setNameSpace(keyRef.getNameSpace());
      }
      Path path = keyRef.getPath();
      for (Element element : path.elements()) {
        ReferenceValuePathElement newElement = new ReferenceValuePathElement();
        newElement.setType(element.getType());
        if (element.hasName()) {
          newElement.setName(element.getName());
        }
        if (element.hasId()) {
          newElement.setId(element.getId());
        }
        refValue.addPathElement(newElement);
      }

      return refValue;
    }

    private static Reference toReference(ReferenceValue refValue) {
      Reference reference = new Reference();
      reference.setApp(refValue.getApp());
      if (refValue.hasNameSpace()) {
        reference.setNameSpace(refValue.getNameSpace());
      }
      Path path = new Path();
      for (ReferenceValuePathElement element : refValue.pathElements()) {
        Element newElement = new Element();
        newElement.setType(element.getType());
        if (element.hasName()) {
          newElement.setName(element.getName());
        }
        if (element.hasId()) {
          newElement.setId(element.getId());
        }
        path.addElement(newElement);
      }
      reference.setPath(path);
      return reference;
    }
  }

  /**
   * The non-indexable blob type.
   */
  private static class BlobType extends BaseBlobType<Blob> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.BLOB;
    }

    @Override
    public boolean isType(EntityV4.ValueOrBuilder propertyValue) {
      return getV3MeaningOf(propertyValue) == Meaning.NO_MEANING
          && propertyValue.getIndexed() == false
          && hasValue(propertyValue);
    }

    @Override
    public boolean hasValue(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.hasBlobValue();
    }

    @Override
    public Blob getValue(EntityV4.ValueOrBuilder propertyValue) {
      return fromDatastoreValue(propertyValue.getBlobValue().toByteArray());
    }

    @Override
    protected Blob fromDatastoreValue(byte[] datastoreValue) {
      return new Blob(datastoreValue);
    }

    @Override
    protected byte[] toDatastoreValue(Object value) {
      return ((Blob) value).getBytes();
    }

    @Override
    public boolean isIndexable() {
      return false;
    }
  }

  /**
   * The indexable blob type.
   */
  private static class ShortBlobType extends BaseBlobType<ShortBlob> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.BYTESTRING;
    }

    @Override
    public boolean isType(EntityV4.ValueOrBuilder propertyValue) {
      if (!hasValue(propertyValue)) {
        return false;
      }

      if (propertyValue.getIndexed()) {
        return getV3MeaningOf(propertyValue) == Meaning.NO_MEANING;
      } else {
        return getV3MeaningOf(propertyValue) == getV3Meaning();
      }
    }

    @Override
    public EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
      EntityV4.Value.Builder builder = super.toV4Value(value, indexed);
      if (!indexed) {
        builder.setMeaning(getV3Meaning().getValue());
      }
      return builder;
    }

    @Override
    public boolean hasValue(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.hasBlobValue()
          || (getV3MeaningOf(propertyValue) == Meaning.INDEX_VALUE
              && propertyValue.hasStringValue());
    }

    @Override
    public ShortBlob getValue(EntityV4.ValueOrBuilder propertyValue) {
      if (getV3MeaningOf(propertyValue) == Meaning.INDEX_VALUE && propertyValue.hasStringValue()) {
        return fromDatastoreValue(propertyValue.getStringValueBytes().toByteArray());
      } else {
        return fromDatastoreValue(propertyValue.getBlobValue().toByteArray());
      }
    }

    @Override
    protected byte[] toDatastoreValue(Object value) {
      return ((ShortBlob) value).getBytes();
    }

    @Override
    protected ShortBlob fromDatastoreValue(byte[] datastoreValue) {
      return new ShortBlob(datastoreValue);
    }

    @Override
    public boolean isIndexable() {
      return true;
    }
  }

  /**
   * The entity type.
   *
   * Stored as a partially serialized EntityProto in V3.
   */
  private static final class EmbeddedEntityType extends Type<EmbeddedEntity> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.ENTITY_PROTO;
    }

     @Override
    public boolean isType(EntityV4.ValueOrBuilder propertyValue) {
      return getV3MeaningOf(propertyValue) == Meaning.NO_MEANING
          && hasValue(propertyValue);
    }

    @Override
    public boolean hasValue(PropertyValue propertyValue) {
      return propertyValue.hasStringValue();
    }

    @Override
    public boolean hasValue(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.hasEntityValue();
    }

    @Override
    public EmbeddedEntity getValue(PropertyValue propertyValue) {
      EntityProto proto = new EntityProto();
      proto.mergeFrom(propertyValue.getStringValueAsBytes());
      EmbeddedEntity result = new EmbeddedEntity();
      if (proto.hasKey() && !proto.getKey().getApp().isEmpty()) {
        result.setKey(KeyTranslator.createFromPb(proto.getKey()));
      }
      extractPropertiesFromPb(proto, result.getPropertyMap());
      return result;
    }

    @Override
    public EmbeddedEntity getValue(EntityV4.ValueOrBuilder propertyValue) {
      EmbeddedEntity result = new EmbeddedEntity();
      EntityV4.Entity proto = propertyValue.getEntityValue();
      if (proto.hasKey()) {
        result.setKey(toKey(proto.getKey()));
      }
      extractPropertiesFromPb(proto, false, result.getPropertyMap());
      return result;
    }

    @Override
    public boolean toV3Value(Object value, PropertyValue propertyValue) {
      EmbeddedEntity structProp = (EmbeddedEntity) value;
      EntityProto proto = new EntityProto();
      if (structProp.getKey() != null) {
        proto.setKey(KeyTranslator.convertToPb(structProp.getKey()));
      }
      addPropertiesToPb(structProp.getPropertyMap(), proto);
      propertyValue.setStringValueAsBytes(proto.toByteArray());
      return false;
    }

    @Override
    public EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
      EmbeddedEntity structProp = (EmbeddedEntity) value;
      EntityV4.Value.Builder builder = EntityV4.Value.newBuilder();
      EntityV4.Entity.Builder proto = builder.getEntityValueBuilder();
      if (structProp.getKey() != null) {
        proto.setKey(toV4Key(structProp.getKey()));
      }
      addPropertiesToPb(structProp.getPropertyMap(), proto);
      builder.setIndexed(false);
      return builder;
    }

    @Override
    public Comparable<?> asComparable(Object value) {
      return null;
    }
  }

  /**
   * The non-indexable {@link Text} type.
   */
  private static final class TextType extends BaseStringType<Text> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.TEXT;
    }

    @Override
    public boolean toV3Value(Object value, PropertyValue propertyValue) {
      super.toV3Value(value, propertyValue);
      return false;
    }

    @Override
    public EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
      return super.toV4Value(value, false);
    }

    @Override
    protected Text fromDatastoreValue(String datastoreString) {
      return new Text(datastoreString);
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((Text) value).getValue();
    }

    @Override
    public ComparableByteArray asComparable(Object value) {
      return null;
    }
  }

  /**
   * The {@link BlobKey} type.
   *
   * In V3 dates are just strings with a special meaning.
   */
  private static final class BlobKeyType extends BaseStringType<BlobKey> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.BLOBKEY;
    }

    @Override
    public boolean isType(EntityV4.ValueOrBuilder propertyValue) {
      return getV3MeaningOf(propertyValue) == Meaning.NO_MEANING
          && hasValue(propertyValue);
    }

    @Override
    public boolean hasValue(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.hasBlobKeyValue()
          || (getV3MeaningOf(propertyValue) == Meaning.INDEX_VALUE
              && propertyValue.hasStringValue());
    }

    @Override
    public EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
      EntityV4.Value.Builder builder = EntityV4.Value.newBuilder();
      builder.setBlobKeyValue(toDatastoreValue(value));
      setIndexed(indexed, builder);
      return builder;
    }

    @Override
    public BlobKey getValue(EntityV4.ValueOrBuilder propertyValue) {
      if (getV3MeaningOf(propertyValue) == Meaning.INDEX_VALUE && propertyValue.hasStringValue()) {
        return fromDatastoreValue(propertyValue.getStringValue());
      } else {
        return fromDatastoreValue(propertyValue.getBlobKeyValue());
      }
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((BlobKey) value).getKeyString();
    }

    @Override
    protected BlobKey fromDatastoreValue(String datastoreString) {
      return new BlobKey(datastoreString);
    }
  }

  /**
   * The date type.
   *
   * In V3 dates are just int64s with a special meaning.
   */
  private static final class DateType extends BaseInt64Type<Date> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.GD_WHEN;
    }

    @Override
    public boolean isType(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.getMeaning() == 0 && hasValue(propertyValue);
    }

    @Override
    public boolean hasValue(EntityV4.ValueOrBuilder propertyValue) {
      return propertyValue.hasTimestampMicrosecondsValue()
          || (getV3MeaningOf(propertyValue) == Meaning.INDEX_VALUE
              && propertyValue.hasIntegerValue());
    }

    @Override
    public EntityV4.Value.Builder toV4Value(Object value, boolean indexed) {
      EntityV4.Value.Builder builder = EntityV4.Value.newBuilder();
      builder.setTimestampMicrosecondsValue(toDatastoreValue(value));
      setIndexed(indexed, builder);
      return builder;
    }

    @Override
    public Date getValue(EntityV4.ValueOrBuilder propertyValue) {
      if (getV3MeaningOf(propertyValue) == Meaning.INDEX_VALUE && propertyValue.hasIntegerValue()) {
        return fromDatastoreValue(propertyValue.getIntegerValue());
      } else {
        return fromDatastoreValue(propertyValue.getTimestampMicrosecondsValue());
      }
    }

    @Override
    protected Long toDatastoreValue(Object value) {
      return ((Date) value).getTime() * 1000L;
    }

    @Override
    protected Date fromDatastoreValue(Long datastoreValue) {
      return new Date(datastoreValue / 1000L);
    }
  }

  /**
   * Internally a link is just a string with a special meaning.
   */
  private static final class LinkType extends BaseStringType<Link> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.ATOM_LINK;
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((Link) value).getValue();
    }

    @Override
    protected Link fromDatastoreValue(String datastoreValue) {
      return new Link(datastoreValue);
    }
  }

  /**
   * Internally a category is just a string with a special meaning.
   */
  private static final class CategoryType extends BaseStringType<Category> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.ATOM_CATEGORY;
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((Category) value).getCategory();
    }

    @Override
    protected Category fromDatastoreValue(String datastoreString) {
      return new Category(datastoreString);
    }
  }

  /**
   * Internally a rating is just an int64 with a special meaning.
   */
  private static final class RatingType extends BaseInt64Type<Rating> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.GD_RATING;
    }

    @Override
    protected Long toDatastoreValue(Object value) {
      return (long) ((Rating) value).getRating();
    }

    @Override
    protected Rating fromDatastoreValue(Long datastoreLong) {
      return new Rating(datastoreLong.intValue());
    }
  }

  /**
   * Internally an email is just a string with a special meaning.
   */
  private static final class EmailType extends BaseStringType<Email> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.GD_EMAIL;
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((Email) value).getEmail();
    }

    @Override
    protected Email fromDatastoreValue(String datastoreString) {
      return new Email(datastoreString);
    }
  }

  /**
   * Internally a postal address is just a string with a special meaning.
   */
  private static final class PostalAddressType extends BaseStringType<PostalAddress> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.GD_POSTALADDRESS;
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((PostalAddress) value).getAddress();
    }

    @Override
    protected PostalAddress fromDatastoreValue(String datastoreString) {
      return new PostalAddress(datastoreString);
    }
  }

  /**
   * Internally a phone number is just a string with a special meaning.
   */
  private static final class PhoneNumberType extends BaseStringType<PhoneNumber> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.GD_PHONENUMBER;
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((PhoneNumber) value).getNumber();
    }

    @Override
    protected PhoneNumber fromDatastoreValue(String datastoreString) {
      return new PhoneNumber(datastoreString);
    }
  }

  /**
   * Internally an IM handle is just a string with a special meaning and a
   * well known format.
   */
  private static final class IMHandleType extends BaseStringType<IMHandle> {
    @Override
    public Meaning getV3Meaning() {
      return Meaning.GD_IM;
    }

    @Override
    protected String toDatastoreValue(Object value) {
      return ((IMHandle) value).toDatastoreString();
    }

    @Override
    protected IMHandle fromDatastoreValue(String datastoreString) {
      return IMHandle.fromDatastoreString(datastoreString);
    }
  }

  static Map<Class<?>, Type<?>> getTypeMap() {
    return typeMap;
  }

  /**
   * A wrapper for a {@code byte[]} that implements {@link Comparable}.
   * Comparison algorithm is the same as the prod datastore.
   */
  public static final class ComparableByteArray implements Comparable<ComparableByteArray> {
    private final byte[] bytes;

    public ComparableByteArray(byte[] bytes) {
      this.bytes = bytes;
    }

    @Override
    public int compareTo(ComparableByteArray other) {
      byte[] otherBytes = other.bytes;
      for (int i = 0; i < Math.min(bytes.length, otherBytes.length); i++) {
        int v1 = bytes[i] & 0xFF;
        int v2 = otherBytes[i] & 0xFF;
        if (v1 != v2) {
          return v1 - v2;
        }
      }
      return bytes.length - otherBytes.length;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      return Arrays.equals(bytes, ((ComparableByteArray) obj).bytes);
    }

    @Override
    public int hashCode() {
      int result = 1;
      for (byte b : bytes) {
        result = 31 * result + b;
      }
      return result;
    }
  }

  /**
   * Helper function to only assign the value if it is not already the default.
   */
  private static void setIndexed(boolean indexed, EntityV4.Value.Builder builder) {
    if (indexed != builder.getIndexed()) {
      builder.setIndexed(indexed);
    }
  }

  /**
   * Helper function to only assign the value if it is not already the default.
   */
  private static void setMeaning(int meaning, EntityV4.Value.Builder builder) {
    if (meaning != builder.getMeaning()) {
      builder.setMeaning(meaning);
    }
  }

  private DataTypeTranslator() {
  }
}
