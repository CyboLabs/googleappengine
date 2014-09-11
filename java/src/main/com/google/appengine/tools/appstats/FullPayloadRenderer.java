// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.appengine.tools.appstats;

import com.google.appengine.tools.appstats.InternalProtos.EmptyProto;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

/**
 * Renders the full payload of a request as a string. Potentially large
 * and thus will probably break if too many rpcs within a request will be made.
 * Use with caution.
 *
 */
public class FullPayloadRenderer implements PayloadRenderer {
  /**
   * Maximum length of rendered payload. This is not a hard limit and can be exceeded for datatypes
   * not being trimmed while rendering (e.g. Long, Double)
   */
  private static final int MAX_OUTPUT_SIZE = 500;
  /**
   * Maximum length an item can be rendered to.
   */
  private static final int PER_ITEM_LIMIT = 20;
  /**
   * String to represent trimmed elements.
   */
  private static final String ELLIPSIS = "...";
  private static final int ELLIPSIS_LENGTH = ELLIPSIS.length();
  /**
   * Levels to iterate through for nested collections.
   */
  private static final int DEPTH = 5;

  @Override
  public String renderPayload(String packageName, String methodName, byte[] payload,
      boolean isRequestPayload) {
    try {
      EmptyProto proto = InternalProtos.EmptyProto.newBuilder().mergeFrom(payload).build();
      return TextFormat.printToString(proto.getUnknownFields());
    } catch (InvalidProtocolBufferException e) {
      return "???";
    }
  }

  @Override
  public String renderPayload(String packageName, String methodName, boolean isRequestPayload,
      Object... params) {
    try {
      StringBuilder builder = new StringBuilder();
      builder.append(packageName).append(".").append(methodName);
      builder.append("(");
      int size = 0;
      String separator = "";
      for (Object param : params) {
        builder.append(separator);
        separator = ", ";
        size = format(builder, param, size, DEPTH);
      }
      builder.append(")");
      return builder.toString();
    } catch (Exception ex) {
      return "???";
    }
  }

  private int format(StringBuilder builder, Object val, int size, int depth) {
    String text = null;
    if (val != null) {
      if (isTextType(val)) {
        text = getText(val.toString(), size, true);
        text = "'" + text + "'";
      } else if (isNumberType(val)) {
        text = getText(Objects.toString(val), size, false);
      } else if (isCollectionType(val)) {
        if (depth < 1) {
          text = getText(Objects.toString(val), size, true);
        } else {
          Iterator<?> iter = ((Collection<?>) val).iterator();
          builder.append("{");
          String separator = "";
          while (iter.hasNext()) {
            builder.append(separator);
            separator = ", ";
            size = format(builder, iter.next(), size, depth - 1);
          }
          builder.append("}");
        }
      } else if (isArrayType(val)) {
        text = getText(deepToString(val), size, true);
      } else {
        text = getText(Objects.toString(val), size, true);
      }
    }

    if (text != null) {
      builder.append(text);
      size += text.length();
    }
    return size;
  }

  private String getText(String val, int filled, boolean trimIfRequired) {
    if (val == null || filled >= MAX_OUTPUT_SIZE) {
      return "";
    }

    if (trimIfRequired) {
      int placesLeft = placesLeft(filled);
      if (placesLeft < val.length()) {
        if (placesLeft > ELLIPSIS_LENGTH) {
          val = val.substring(0, placesLeft - ELLIPSIS_LENGTH) + ELLIPSIS;
        } else {
          val = ELLIPSIS;
        }
      }
    }

    filled += val.length();
    return val;
  }

  private String deepToString(Object obj) {
    String s = Arrays.deepToString(new Object[] {obj});
    return s.substring(1, s.length() - 1);
  }

  private static int placesLeft(int filled) {
    return Math.min(MAX_OUTPUT_SIZE - filled, PER_ITEM_LIMIT);
  }

  private static boolean isTextType(Object obj) {
    return obj instanceof CharSequence || obj instanceof Character || obj instanceof Enum;
  }

  private static boolean isNumberType(Object obj) {
    return obj instanceof Number;
  }

  private static boolean isArrayType(Object obj) {
    return (obj != null) && (obj.getClass().isArray());
  }

  private static boolean isCollectionType(Object obj) {
    return obj instanceof Collection;
  }
}
