package com.google.appengine.api.search.checkers;

import com.google.apphosting.api.AppEngineInternal;
import com.google.apphosting.api.search.DocumentPb;

/**
 * Provides checks for Facet names and values: atom or number.
 */
@AppEngineInternal
public final class FacetChecker {

  /**
   * Checks whether a facet name is valid. The facet name length must be
   * between 1 and {@link SearchApiLimits#MAXIMUM_NAME_LENGTH} inclusive, and it should match
   * {@link SearchApiLimits#FACET_NAME_PATTERN}.
   *
   * @param name the facet name to check
   * @return the checked facet name
   * @throws IllegalArgumentException if the facet name is null or empty
   * or is longer than {@literal SearchApiLimits#MAXIMUM_NAME_LENGTH} or it doesn't
   * match {@literal #FACET_NAME_PATTERN}.
   */
  public static String checkFacetName(String name) {
    return checkFacetName(name, "facet name");
  }

  /**
   * Checks whether a facet name is valid. The facet name length must be
   * between 1 and {@link SearchApiLimits#MAXIMUM_NAME_LENGTH} inclusive, and it should match
   * {@link SearchApiLimits#FACET_NAME_PATTERN}.
   *
   * @param name the facet name to check
   * @param callerContext the caller context used for creating error message in case of a failure.
   * @return the checked facet name
   * @throws IllegalArgumentException if the facet name is null or empty
   * or is longer than {@literal SearchApiLimits#MAXIMUM_NAME_LENGTH} or it doesn't
   * match {@literal #FACET_NAME_PATTERN}.
   */
  public static String checkFacetName(String name, String callerContext) {
    return FieldChecker.checkFieldName(name, callerContext);
  }

  /**
   * Checks whether an atom is valid. An atom can be null or a string between
   * 1 and {@literal SearchApiLimits.MAXIMUM_ATOM_LENGTH} in length, inclusive.
   *
   * @param atom the atom to check
   * @return the checked atom
   * @throws IllegalArgumentException if atom is too long or too short (i.e. empty)
   */
  public static String checkAtom(String atom) {
    FieldChecker.checkAtom(atom);
    Preconditions.checkArgument(!atom.isEmpty(), "Facet atom is empty");
    return atom;
  }

  /**
   * Checks whether a number is valid. A number can be null or a value between
   * {@link SearchApiLimits#MINIMUM_NUMBER_VALUE} and {@link SearchApiLimits#MAXIMUM_NUMBER_VALUE},
   * inclusive.
   *
   * @param value the value to check
   * @return the checked number
   * @throws IllegalArgumentException if number is out of range
   */
  public static Double checkNumber(Double value) {
    return FieldChecker.checkNumber(value);
  }

  /**
   * Checks whether a facet value is valid.
   *
   * @param value the facet value to check
   * @return the checked facet value
   * @throws IllegalArgumentException if the facet value type is not recognized or
   * if the facet value string is not valid based on the type. See {@link #checkNumber}
   *     and {@link #checkAtom}.
   */
  public static DocumentPb.FacetValue checkFacetValue(DocumentPb.FacetValue value) {
    if (value != null) {
      switch (value.getType()) {
        case ATOM:
          checkAtom(value.getStringValue());
          break;
        case NUMBER:
          checkNumber(Double.parseDouble(value.getStringValue()));
          break;
        default:
          throw new IllegalArgumentException("Unsupported facet type: " + value.getType());
      }
    }
    return value;
  }

  public static DocumentPb.Facet checkValid(DocumentPb.Facet facet) {
    checkFacetName(facet.getName());
    checkFacetValue(facet.getValue());
    return facet;
  }

  private FacetChecker() {}
}
