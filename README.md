#PDF/A Converter Service

This project is a server-based service that has the PDF/A Converter project build artifact as a Maven dependency. This is reflected in this project's POM file.
The PDF/A Converter project is located here: https://github.com/harvard-lts/drs-pdfa-conversion.

This has been tested on Tomcat 8 and requires Java 8.


* <a href="#servlet-usage">PDF/A Converter Service Usage Notes</a>
* <a href="#tomcat">Deploying to Tomcat</a>
* <a href="#ide-notes">IDE Notes</a>

## <a name="servlet-usage"></a>PDF/A Converter Service Usage Notes
**This project requires the installation of [PDF/A Converter Utility](https://github.com/harvard-lts/drs-pdfa-conversion)**

Build the PDF/A Converter Utility (ZIP file) and PDF/A Converter Service (WAR file). Unpack the ZIP file to a directory on the server. Take note of this directory. The WAR file should be placed in the Tomcat 'webapps' directory.

In order to run the PDF/A Converter Service on a server it’s necessary to modify the server’s classpath configuration to add PDF/A Converter Utility JAR files. Essentially, this mean adding the PDF/A Converter Utility home directory to the server’s classpath since the PDF/A Converter Utility can (and should) be deployed to a location outside the server. See below for how to do this in Tomcat.

The WAR file can be built from the source code using Maven. Since the WAR file contains the version in the file name, it may be desirable to shorten this file name by removing this version number or just renaming the path to the application within the application server used to deploy the application. Note: The PDF/A Converter Service version is contained within the WAR file's manifest file. Here is an example of the base URL for accessing this application without modification to the WAR file:
    `http://yourserver.yourdomain.com:<port>/pdfa-converter-service/<endpoint>`
The `<endpoint>` is one of the endpoints available within the Service plus parameters to access the service.

### Endpoints
There are currently two services provided by the web application.
#### 1. /convert
Converts a word processing file and returns a PDF/A document for download.
    Substitute 'convert' for `<endpoint>` (see above) plus add a 'file' parameter name with the path to the input file for a GET request or submit a POST request with form data with a 'file' parameter name containing the contents of the file as its payload.
<br>Examples: 
* GET: (using curl) `curl --get -k --data-binary file=path/to/file http://yourserver.yourdomain.com:<port>/pdfa-converter-service/convert`
* GET: (using a browser) `http://yourserver.yourdomain.com:<port>/pdfa-converter-service/convert?file=path/to/file`
* POST: (using curl) `curl -k -F datafile=@path/to/file http://yourserver.yourdomain.com:<port>/pdfa-converter-service/convert` ('datafile' is the required form parameter that points to the uploaded file.)

#### 2. /version
Obtaining the version of PDF/A Converter Utility being used to convert input files returned as plain text format. (GET request only)
<br>Examples:
* GET (using curl) `curl --get http://yourserver.yourdomain.com:<port>/pdfa-converter-service/version`
* GET (using a browser) `http://yourserver.yourdomain.com:<port>/pdfa-converter-service/version`

### Web Interface
There is also a web page with a form for uploading a file for PDF/A Converter processing at the root of the application. It can be access from this URL:
`http://yourserver.yourdomain.com:<port>/pdfa-converter-service/`
The converted PDF/A document will be returned as a file to download.

## <a name="tomcat"></a>Deploying to Tomcat 8
### Add Entries to catalina.properties
It’s necessary to add the location of the PDF/A Converter Utility home directory to the file `$CATALINA_BASE/conf/catalina.properties` then add the location of the PDF/A Converter Utility lib folder JAR files. (See example below.) 
<br>1. Add the “pdfaConverter.home” environment variable.
<br>2. Add all “pdfaConverter.home”/lib/ JAR files to the shared class loader classpath with a wildcard ‘*’ and the `${pdfaConverter.home}` property substitution.
<br>3. Create a file, pdfa-service.properties, for the location of the external applications necessary for converting the various word processing documents to PDF/A format. The default version of this file is in the src/main/resources directory. This file should be added to the catalina.properties file as well with the value: `PDFA_CONVERTER_PROPS=/path/to/pdfa-service.properties`
<br>4. (optional) Rather than using the default log4j2.xml file built into the WAR file and resides in the src/main/resources folder it's possible (and encouraged) to set up logging to 
point to an external log4j2.xml. Add a "log4j.configurationFile" property to catalina.properties pointing to this file.
<br>5. (optional) Create an external properties file to change the default configuration values for Servlet upload file size and other values contained in WEB-INF/classes/pdfa-service.properties.
#### catalina.properties example
Add the following to the bottom of the file:
- `pdfaConverter.home=path/to/drs-pdfa-converter/home` (note: no final slash in path)
- `shared.loader=${pdfaConverter.home}/lib/*.jar`
- `PDFA_CONVERTER_PROPS=/path/to/pdfa-service.properties`
- `log4j.configuration=/path/to/log4j2.xml` or `log4j.configuration=/path/to/log4j2.xmls` (optional -- to override using the default log4j2.xml.) 
- `PDFA_SERVICE_PROPS=/path/to/pdfa-service.properties` (optional -- to override default values contained within WAR file.)

#### Additional Information:
**Class loading:** Within the WAR file’s META-INF directory is a Tomcat-specific file, context.xml. This file indicates to the Tomcat to modify the default class loader scheme for this application. The result is that, rather than load the WAR’s classes and JAR files first, classes on Tomcat’s shared classpath will be loaded first. This is important so that log4j in the PDF/A Converter Utility uses the same log4j configuration file as set in the web application.

## <a name="ide-notes"></a>IDE Notes 
For Eclipse and other IDE's it will be necessary to resolve reference to classes in the PDF/A Converter Utility project. When adding this project to an IDE for development the PDF/A Converter Utility project should also be added and referenced.
