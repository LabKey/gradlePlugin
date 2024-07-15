package org.labkey.gradle.util

class ExternalDependency implements Serializable
{
    public static final String APACHE_2_LICENSE_NAME = "Apache 2.0"
    public static final String APACHE_2_LICENSE_URL = "http://www.apache.org/licenses/LICENSE-2.0"
    public static final String MIT_LICENSE_NAME = "MIT"
    public static final String MIT_LICENSE_URL = "http://www.opensource.org/licenses/mit-license.php"
    public static final String GNU_LESSER_GPL_21_NAME = "GNU Lesser GPL V2.1"
    public static final String GNU_LESSER_GPL_21_URL = "http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html"
    public static final String LGPL_LICENSE_NAME = "LGPL"
    public static final String LGPL_LICENSE_URL = "http://www.gnu.org/copyleft/lesser.html"
    public static final String BSD_2_LICENSE_NAME = "BSD 2"
    public static final String BSD_2_LICENSE_URL = "https://opensource.org/licenses/BSD-2-Clause"
    public static final String BSD_LICENSE_NAME = "BSD"
    public static final String BSD_LICENSE_URL = "http://www.opensource.org/licenses/bsd-license.php"

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
