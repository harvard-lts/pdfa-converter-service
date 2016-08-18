/*
Copyright (c) 2016 by The President and Fellows of Harvard College
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License. You may obtain a copy of the License at:
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permission and limitations under the License.
*/
package edu.harvard.hul.ois.drs.pdfaconvert.service.common;

public class Constants {

	/** Environment variable for setting path to external application properties file */
	public final static String ENV_PROJECT_PROPS = "PDFA_SERVICE_PROPS";

	/** Name of application properties file */
	public final static String PROPERTIES_FILE_NAME = "pdfa-service.properties";

	/** Resource path for processing an input file */
	public final static String RESOURCE_PATH_CONVERT = "/convert";

	/** Resource path for obtaining the PDF/A Utility version (GET only) */
    public final static String RESOURCE_PATH_VERSION = "/version";

	/** Form variable name for access to input file (POST) */
    public final static String FORM_FIELD_DATAFILE = "datafile";

	/** Request parameter name for pointing to input file (GET) */
    public final static String FILE_PARAM = "file";

	/** Key for placing input file name into Request */
    public final static String TEMP_FILE_NAME_KEY = "tempFilename";
    
    public final static String TEXT_PLAIN_MIMETYPE = "text/plain";
    public final static String TEXT_XML_MIMETYPE = "text/xml";
    public final static String PDF_MIMETYPE = "application/pdf";
}
