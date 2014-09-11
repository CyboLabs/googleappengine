// Copyright 2010 Google Inc. All rights reserved.
package com.google.appengine.api.taskqueue;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * One or more task names already exists in the queue.
 *
 */
public class TaskAlreadyExistsException extends RuntimeException {
  private final List<String> tasknames = new LinkedList<String>();

  public TaskAlreadyExistsException(String detail) {
    super(detail);
  }

  /**
   * Returns a list of the names of the tasks that already exist, in the same order as they were
   * given in the call to add(). Only some of the methods that throw a
   * {@code TaskAlreadyExistsException} will populate this list. Otherwise it will be an empty
   * list.
   */
  public List<String> getTaskNames() {
    return Collections.unmodifiableList(tasknames);
  }

  /**
   * Appends "name" to the end of the list of names of tasks that could not be added because a task
   * with that name already exists in the specified queue.
   */
  void appendTaskName(String name) {
    tasknames.add(name);
  }

}
