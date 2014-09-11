// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.appengine.api.files;

import com.google.appengine.spi.ServiceFactoryFactory;

/**
 * A factory for producing instances of {@link FileService}.
 *
 *
 * @deprecated This api has been deprecated in favor of the App Engine GCS client.
 * @see <a href="https://developers.google.com/appengine/docs/java/googlecloudstorageclient/">
 *       App Engine GCS client documentation</a>
 */
@Deprecated
public class FileServiceFactory {
  /**
   * Returns an instance of {@link FileService}.
   */
  public static FileService getFileService() {
    return getFactory().getFileService();
  }

  private static IFileServiceFactory getFactory() {
    return ServiceFactoryFactory.getFactory(IFileServiceFactory.class);
  }
}
