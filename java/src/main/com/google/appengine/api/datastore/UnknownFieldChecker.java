package com.google.appengine.api.datastore;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.io.protocol.MessageSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.UnknownFieldSet.Field;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A utility for finding unknown fields in a protocol buffer.
 */
class UnknownFieldChecker {

  private final Set<Class<? extends MessageOrBuilder>> messagesToIgnore;

  UnknownFieldChecker() {
    this(ImmutableSet.<Class<? extends MessageOrBuilder>>of());
  }

  /**
   * @param messagesToIgnore A set of message classes within which unknown
   *     fields will be ignored.
   */
  UnknownFieldChecker(
      Set<Class<? extends MessageOrBuilder>> messagesToIgnore) {
    this.messagesToIgnore = messagesToIgnore;
  }

  /**
   * Returns a set of unknown fields in the provided message.
   */
  Set<String> getUnknownFields(MessageOrBuilder message) {
    Set<String> unknownFieldNames = new HashSet<>();
    collectUnknownFields(null, message, unknownFieldNames);
    return unknownFieldNames;
  }

  @SuppressWarnings("unchecked")
  private void collectUnknownFields( FieldDescriptor parentFieldDescriptor,
      MessageOrBuilder message, Set<String> unknownFieldsCollector) {
    if (messagesToIgnore.contains(message.getClass())
        || message.getClass().equals(MessageSet.class)) {
      return;
    }
    Map<Integer, Field> unknownFieldMap = message.getUnknownFields().asMap();
    Descriptor messageDescriptor = message.getDescriptorForType();
    for (Integer unknownTag : unknownFieldMap.keySet()) {
      if (!messageDescriptor.isExtensionNumber(unknownTag)) {
        String fieldName = parentFieldDescriptor == null
            ? "" : parentFieldDescriptor.getName();
        unknownFieldsCollector.add(
            String.format("%s[tag=%d]", fieldName, unknownTag));
      }
    }
    for (Map.Entry<FieldDescriptor, Object> field : message.getAllFields().entrySet()) {
      FieldDescriptor fieldDescriptor = field.getKey();
      if (fieldDescriptor.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
        Object fieldValue = field.getValue();
        List<MessageOrBuilder> subMessages = fieldDescriptor.isRepeated()
            ? (List<MessageOrBuilder>) fieldValue
            : ImmutableList.of((MessageOrBuilder) fieldValue);
        for (MessageOrBuilder subMessage : subMessages) {
          collectUnknownFields(fieldDescriptor, subMessage, unknownFieldsCollector);
        }
      }
    }
  }
}
