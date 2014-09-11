package com.google.appengine.tools.development;

import com.google.common.base.Joiner;

import javax.annotation.concurrent.GuardedBy;

/**
 * Holder for the state of a module or backend instance.
 */
public class InstanceStateHolder {
  static enum InstanceState {
    INITIALIZING, SLEEPING, RUNNING_START_REQUEST, RUNNING, STOPPED, SHUTDOWN;
  }

  private static final Joiner STATE_JOINER = Joiner.on("|");
  private final String moduleOrBackendName;
  private final int instance;
  @GuardedBy("this")
  private InstanceState currentState = InstanceState.SHUTDOWN;

  /**
   * Constructs an {@link InstanceStateHolder}.
   *
   * @param moduleOrBackendName For module instances the module name and for backend instances the
   *     backend name.
   * @param instance The instance number or -1 for load balancing instances and automatic module
   * instances.
   */
  InstanceStateHolder(String moduleOrBackendName, int instance) {
    this.moduleOrBackendName = moduleOrBackendName;
    this.instance = instance;
  }

  /**
   * Updates the current instance state and verifies that the previous state is
   * what is expected.
   *
   * @param newState The new state to change to
   * @param acceptablePreviousStates Acceptable previous states
   * @throws IllegalStateException If the current state is not one of the
   *         acceptable previous states
   */
  void testAndSet(InstanceState newState,
      InstanceState... acceptablePreviousStates) throws IllegalStateException {
    InstanceState invalidState =
        testAndSetIf(newState, acceptablePreviousStates);
    if (invalidState != null) {
      reportInvalidStateChange(moduleOrBackendName, instance, invalidState,
          newState, acceptablePreviousStates);
    }
  }

  /**
   * Reports an invalid state change attempt.
   */
  static void reportInvalidStateChange(String moduleOrBackendName, int instance,
      InstanceState currentState, InstanceState newState,
      InstanceState... acceptablePreviousStates) {
    StringBuilder error = new StringBuilder();
    error.append("Tried to change state to " + newState);
    error.append(" on module " + moduleOrBackendName + "." + instance);
    error.append(" but previous state is " + currentState);
    error.append(" and not ");
    error.append(STATE_JOINER.join(acceptablePreviousStates));
    throw new IllegalStateException(error.toString());
  }

  /**
   * Updates the instance state to the requested value and returns
   * null if the previous state is an acceptable value and if not leaves the
   * current module state unchanged and returns the current invalid state.
   */
  synchronized InstanceState testAndSetIf(InstanceState newState,
      InstanceState... acceptablePreviousStates) {
    InstanceState result = currentState;
    if (test(acceptablePreviousStates)) {
      result = null;
      currentState = newState;
    }
    return result;
  }

  /**
   * Returns true if current state is one of the provided acceptable states.
   */
  synchronized boolean test(InstanceState... acceptableStates) {
    for (InstanceState acceptable : acceptableStates) {
      if (currentState == acceptable) {
        return true;
      }
    }
    return false;
  }

  /**
   * Throws an IllegalStateException if the current state is not one of the
   * acceptable states for the designated operation.
   */
  synchronized void requireState(String operation, InstanceState... acceptableStates) {
    if (!test(acceptableStates)) {
      throw new IllegalStateException("Invalid current state operation=" + operation
          + " currentState=" + currentState
          + " acceptableStates=" + STATE_JOINER.join(acceptableStates));
    }
  }

  /**
   * Checks if the instance is in a state where it can accept incoming requests.
   *
   * @return true if the instance can accept incoming requests, false otherwise.
   */
  synchronized boolean acceptsConnections() {
    return (currentState == InstanceState.RUNNING
        || currentState == InstanceState.RUNNING_START_REQUEST
        || currentState == InstanceState.SLEEPING);
  }

  /**
   * Returns the display name for the current state.
   */
  synchronized String getDisplayName() {
    return currentState.name().toLowerCase();
  }

  /**
   * Unconditionally sets the state.
   */
  synchronized void set(InstanceState newState) {
    currentState = newState;
  }
}
