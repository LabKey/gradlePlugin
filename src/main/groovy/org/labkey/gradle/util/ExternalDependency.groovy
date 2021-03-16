package org.labkey.gradle.util

class ExternalDependency
{
    private String coordinates
    private String licenseName
    private String licenseURL
    private String source
    private String sourceURL
    private String purpose

    ExternalDependency() {

    }

    ExternalDependency(String coordinates, String licenseName, String licenseURL, String source, String sourceURL, String purpose)
    {
        this.coordinates = coordinates;
        this.licenseName = licenseName;
        this.licenseURL = licenseURL;
        this.source = source;
        this.sourceURL = sourceURL
        this.purpose = purpose
    }

    String getCoordinates()
    {
        return coordinates
    }

    void setCoordinates(String coordinates)
    {
        this.coordinates = coordinates
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

//    String toString()
//    {
//        StringBuilder builder = new StringBuilder()
//
//    }
}
