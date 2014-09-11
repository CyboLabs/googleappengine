// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development.agent.impl;

import java.lang.instrument.Instrumentation;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Performs the actual agent work.
 *
 */
public class AgentImpl implements Agent {

  static final String AGENT_RUNTIME =
      "com/google/appengine/tools/development/agent/runtime/Runtime";

  private static final AgentImpl instance = new AgentImpl();

  private final Set<ClassLoaderReference> appUrlClassLoaders = new HashSet<ClassLoaderReference>();
  private final ReferenceQueue<ClassLoader> classLoaderReferenceQueue =
      new ReferenceQueue<ClassLoader>();

  public void run(Instrumentation instrumentation,
      boolean treatRestrictedClassListViolationsAsErrors) {
    instrumentation.addTransformer(new Transformer(treatRestrictedClassListViolationsAsErrors));
  }

  @Override
  public Set<String> getBlackList() {
    return BlackList.getBlackList();
  }

  @Override
  public synchronized void recordAppClassLoader(ClassLoader loader) {
    prune();
    appUrlClassLoaders.add(new ClassLoaderReference(loader));
  }

  public static AgentImpl getInstance() {
    return instance;
  }

  public synchronized boolean isAppConstructedURLClassLoader(ClassLoader loader) {
    prune();
    return appUrlClassLoaders.contains(new ClassLoaderReference(loader));
  }

  private void prune() {
    boolean any = false;
    while (classLoaderReferenceQueue.poll() != null) {
      any = true;
    }
    if (any) {
      for (Iterator<ClassLoaderReference> it = appUrlClassLoaders.iterator(); it.hasNext(); ) {
        ClassLoaderReference classLoaderReference = it.next();
        if (classLoaderReference.get() == null) {
          it.remove();
        }
      }
    }
  }

  private class ClassLoaderReference {
    private final WeakReference<ClassLoader> classLoaderReference;
    private final int hashCode;

    ClassLoaderReference(ClassLoader classLoader) {
      this.classLoaderReference =
          new WeakReference<ClassLoader>(classLoader, classLoaderReferenceQueue);
      this.hashCode = System.identityHashCode(classLoader);
    }

    ClassLoader get() {
      return classLoaderReference.get();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof ClassLoaderReference) {
        ClassLoaderReference that = (ClassLoaderReference) obj;
        return get() == that.get();
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return hashCode;
    }
  }
}
