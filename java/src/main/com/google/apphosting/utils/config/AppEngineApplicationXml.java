package com.google.apphosting.utils.config;

/**
 * Holder for appengine-applicaion.xml properties.
 */
public class AppEngineApplicationXml {
  private final String applicationId;

  private AppEngineApplicationXml(String applicationId) {
    this.applicationId = applicationId;
  }

  public String getApplicationId() {
    return applicationId;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    return prime + ((applicationId == null) ? 0 : applicationId.hashCode());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj){
      return true;
    }
    if (obj == null){
      return false;
    }
    if (getClass() != obj.getClass()){
      return false;
    }
    AppEngineApplicationXml other = (AppEngineApplicationXml) obj;
    if (applicationId == null) {
      if (other.applicationId != null){
        return false;
      }
    } else if (!applicationId.equals(other.applicationId)){
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AppEngineApplicationXml: application=" + applicationId;
  }

  /**
   * Builder for an {@link AppEngineApplicationXml}
   */
  static class Builder{
    private String applicationId;

    Builder setApplicationId(String applicationId) {
      this.applicationId = applicationId;
      return this;
    }

    AppEngineApplicationXml build() {
      return new AppEngineApplicationXml(applicationId);
    }
  }
}
