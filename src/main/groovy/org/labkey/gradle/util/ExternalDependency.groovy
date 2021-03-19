package org.labkey.gradle.util

class ExternalDependency
{
    private String configuration = "external"
    private String coordinates
    private String component
    private String licenseName
    private String licenseURL
    private String source
    private String sourceURL
    private String purpose

    ExternalDependency() { }

    ExternalDependency(String coordinates, String component, String source, String sourceURL, String licenseName, String licenseURL, String purpose)
    {
        this("external", coordinates, component, source, sourceURL, licenseName, licenseURL, purpose)
    }

    ExternalDependency(String configuration, String coordinates, String component, String source, String sourceURL, String licenseName, String licenseURL, String purpose)
    {
        this.configuration = configuration
        this.coordinates = coordinates
        this.component = component
        this.source = source
        this.sourceURL = sourceURL
        this.licenseName = licenseName
        this.licenseURL = licenseURL
        this.purpose = purpose
    }

    String getConfiguration()
    {
        return configuration
    }

    void setConfiguration(String configuration)
    {
        this.configuration = configuration
    }

    String getCoordinates()
    {
        return coordinates
    }

    void setCoordinates(String coordinates)
    {
        this.coordinates = coordinates
    }

    String getComponent()
    {
        return component
    }

    void setComponent(String component)
    {
        this.component = component
    }

    String getLicenseName()
    {
        return licenseName
    }

    void setLicenseName(String licenseName)
    {
        this.licenseName = licenseName
    }

    String getLicenseURL()
    {
        return licenseURL
    }

    void setLicenseURL(String licenseURL)
    {
        this.licenseURL = licenseURL
    }

    String getSource()
    {
        return source
    }

    void setSource(String source)
    {
        this.source = source
    }

    String getSourceURL()
    {
        return sourceURL
    }

    void setSourceURL(String sourceURL)
    {
        this.sourceURL = sourceURL
    }

    String getPurpose()
    {
        return purpose
    }

    void setPurpose(String purpose)
    {
        this.purpose = purpose
    }
}
