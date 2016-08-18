/*
Copyright (c) 2016 by The President and Fellows of Harvard College
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License. You may obtain a copy of the License at:
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permission and limitations under the License.
*/
package edu.harvard.hul.ois.drs.pdfaconvert.service.servlets;

import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.ENV_PROJECT_PROPS;
import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.FILE_PARAM;
import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.FORM_FIELD_DATAFILE;
import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.PDF_MIMETYPE;
import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.PROPERTIES_FILE_NAME;
import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.RESOURCE_PATH_CONVERT;
import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.RESOURCE_PATH_VERSION;
import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.TEMP_FILE_NAME_KEY;
import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.TEXT_PLAIN_MIMETYPE;
import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.TEXT_XML_MIMETYPE;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import edu.harvard.hul.ois.drs.pdfaconvert.ExternalToolException;
import edu.harvard.hul.ois.drs.pdfaconvert.GeneratedFileUnavailableException;
import edu.harvard.hul.ois.drs.pdfaconvert.PdfaConverterOutput;
import edu.harvard.hul.ois.drs.pdfaconvert.UnknownFileTypeException;
import edu.harvard.hul.ois.drs.pdfaconvert.service.common.DiskFileItemExt;
import edu.harvard.hul.ois.drs.pdfaconvert.service.common.ErrorMessage;
import edu.harvard.hul.ois.drs.pdfaconvert.service.pool.PdfaConverterWrapper;
import edu.harvard.hul.ois.drs.pdfaconvert.service.pool.PdfaConverterWrapperFactory;
import edu.harvard.hul.ois.drs.pdfaconvert.service.pool.PdfaConverterWrapperPool;

/**
 * Handles the upload of a file either locally or remotely for processing by
 * Pdfa Converter. For a local upload HTTP GET is used by having a request parameter point
 * to the local file's location. For a remote upload HTTP POST is used to pass
 * the in the file as form data.
 */
@WebServlet(name="PDF-A Converter Servlet", urlPatterns={RESOURCE_PATH_CONVERT, RESOURCE_PATH_VERSION}, loadOnStartup=1, asyncSupported=false)
public class PdfaConverterServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static final String UPLOAD_DIRECTORY = "java.io.tmpdir";
	private static final int MIN_IDLE_OBJECTS_IN_POOL = 3;
	private static final String DEFAULT_MAX_OBJECTS_IN_POOL = "10";
	private static final String DEFAULT_MAX_UPLOAD_SIZE = "40";  // in MB
	private static final String DEFAULT_MAX_REQUEST_SIZE = "50"; // in MB
	private static final String DEFAULT_IN_MEMORY_FILE_SIZE = "3"; // in MB - above which the temporary file is stored to disk
	private static final long MB_MULTIPLIER = 1024 * 1024;
	private static int poolUsageCount = 0; // for testing usage of pool objects
	private static final Logger logger = LogManager.getLogger();
	private static final Marker POOL_MARKER = MarkerManager.getMarker("POOL");

	private PdfaConverterWrapperPool pdfaConverterWrapperPool;
	private DiskFileItemFactory factory;
	private ServletFileUpload upload;
	private Properties applicationProps = null;
	private int maxInMemoryFileSizeMb;

	@Override
	public void init() throws ServletException {
		
		// Set the projects properties.
		// First look for a system property pointing to a project properties file.
		// This value can be either a file path, file protocol (e.g. - file:/path/to/file),
		// or a URL (http://some/server/file).
		// If this value either does not exist or is not valid, the default
		// file that comes with this application will be used for initialization.
		String environmentProjectPropsFile = System.getProperty(ENV_PROJECT_PROPS);
		logger.info("Value of environment property: [{}] for finding external properties file in location: {}",ENV_PROJECT_PROPS,  environmentProjectPropsFile);
		if (environmentProjectPropsFile != null) {
			logger.info("Will look for properties file from environment in location: {}", environmentProjectPropsFile);
			try {
				File projectProperties = new File(environmentProjectPropsFile);
				if (projectProperties.exists() && projectProperties.isFile() && projectProperties.canRead()) {
					InputStream is = new FileInputStream(projectProperties);
					applicationProps = new Properties();
					applicationProps.load(is);
				}
			} catch (IOException e) {
				// fall back to default file
				logger.error("Unable to load properties file: {} -- reason: {}", environmentProjectPropsFile, e.getMessage());
				logger.error("Falling back to default project.properties file: {}", PROPERTIES_FILE_NAME);
				applicationProps = null;
			}
		}

		if (applicationProps == null) { // did not load from environment variable location
			try {
				ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
				InputStream resourceStream = classLoader.getResourceAsStream(PROPERTIES_FILE_NAME);
				if (resourceStream != null) {
					applicationProps = new Properties();
					applicationProps.load(resourceStream);
					logger.info("loaded default applicationProps");
				} else {
					logger.warn("project.properties not found!!!");
				}
			} catch (IOException e) {
				logger.error("Could not load properties file: {}", PROPERTIES_FILE_NAME, e);
				// couldn't load default properties so bail...
				throw new ServletException("Couldn't load an applications properties file.", e);
			}
		}
		int maxPoolSize = Integer.valueOf(applicationProps.getProperty("max.objects.in.pool", DEFAULT_MAX_OBJECTS_IN_POOL));
		long maxFileUploadSizeMb = Long.valueOf(applicationProps.getProperty("max.upload.file.size.MB", DEFAULT_MAX_UPLOAD_SIZE));
		long maxRequestSizeMb = Long.valueOf(applicationProps.getProperty("max.request.size.MB", DEFAULT_MAX_REQUEST_SIZE));
		maxInMemoryFileSizeMb = Integer.valueOf(applicationProps.getProperty("max.in.memory.file.size.MB", DEFAULT_IN_MEMORY_FILE_SIZE));
		logger.info("Max objects in object pool: {} -- Max file upload size: {}MB -- Max request object size: {}MB -- Max in-memory file size: {}MB",
				maxPoolSize, maxFileUploadSizeMb, maxRequestSizeMb, maxInMemoryFileSizeMb);

		logger.debug("Initializing PdfaConverter pool");
		GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
		poolConfig.setMinIdle(MIN_IDLE_OBJECTS_IN_POOL);
		poolConfig.setMaxTotal(maxPoolSize);
		poolConfig.setTestOnBorrow(true);
		poolConfig.setBlockWhenExhausted(true);
		pdfaConverterWrapperPool = new PdfaConverterWrapperPool(new PdfaConverterWrapperFactory(), poolConfig);

		// configures upload settings
		factory = new DiskFileItemFactory();
		factory.setSizeThreshold((maxInMemoryFileSizeMb * (int)MB_MULTIPLIER));
		File tempUploadDir = new File(System.getProperty(UPLOAD_DIRECTORY));
		if (!tempUploadDir.exists()) {
			tempUploadDir.mkdir();
		}
		factory.setRepository(tempUploadDir);

		upload = new ServletFileUpload(factory);
		upload.setFileSizeMax(maxFileUploadSizeMb * MB_MULTIPLIER); // convert from MB to bytes
		upload.setSizeMax(maxRequestSizeMb * MB_MULTIPLIER); // convert from MB to bytes

		logger.debug("PdfaConverter pool finished Initializing");
	}
	
	/**
	 * Clean up any leftover files in Servlet container upload directory.
	 * These should have been cleaned up during normal processing.
	 * 
	 * @see javax.servlet.GenericServlet#destroy()
	 */
	@Override
	public void destroy() {
		File tempUploadDir = new File(System.getProperty(UPLOAD_DIRECTORY));
		if (tempUploadDir.exists() && tempUploadDir.listFiles() != null) {
			File[] files = tempUploadDir.listFiles();
			for (File file : files) {
				file.delete();
			}
		}
	}

	/**
	 * Handles the HTTP <code>GET</code> method. There are currently two end
	 * point for GET:
	 * <ol>
	 * <li>/examine -- to have PdfaConverter examine a file and return a PDF/A. Use this when uploading a file locally.</li>
	 * <li>/version -- to receive "text/plain" output of the version of PdfaConverter
	 * being used to process files.</li>
	 * </ol>
	 * "/examine" requires the path to the file to be analyzed with the request
	 * parameter "file" set to location of the file. E.g.: http://
	 * <host>[:port]/pdfa-converter/examine?file=<path/to/file/filename Note: "pdfa-converter" in
	 * the above URL needs to be adjusted to the final name of the WAR file.
	 *
	 * @param request
	 *            servlet request
	 * @param response
	 *            servlet response
	 * @throws ServletException
	 *             if a servlet-specific error occurs
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		String servletPath = request.getServletPath(); // gives servlet mapping
		logger.info("Entering doGet(): " + servletPath);

		// See if path is just requesting version number. If so, just return it.
		// Outputs version of PdfaConverter, not the version of web application.
		if (RESOURCE_PATH_VERSION.equals(servletPath)) {
			sendPdfaConverterVersionResponse(request, response); 
			return;
		}

		String filePath = request.getParameter(FILE_PARAM);
		if (StringUtils.isEmpty(filePath)) {
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_BAD_REQUEST,
					" Missing parameter: [" + FILE_PARAM + "] ", request.getRequestURL().toString());
			sendErrorMessageResponse(errorMessage, response);
			return;
		}
		
		String fileName = null;
        int index = filePath.lastIndexOf(File.separatorChar);
        if (index > 0 && index <= filePath.length()) {
        	fileName = filePath.substring(index + 1);
        } else {
        	fileName = filePath;
        }
        File inputFile = new File(filePath);

        // Send it to the PdfaConverter processor...
		sendPdfaConverterExamineResponse(inputFile, fileName, request, response);
	}

	/**
	 * Handles the file upload for PdfaConverter processing via streaming of the file
	 * using the <code>POST</code> method. Example: curl -X POST -F datafile=@
	 * <path/to/file> <host>:[<port>]/pdfa-converter/examine Note: "pdfa-converter" in the above URL
	 * needs to be adjusted to the final name of the WAR file.
	 *
	 * @param request
	 *            servlet request
	 * @param response
	 *            servlet response
	 * @throws ServletException
	 *             if a servlet-specific error occurs
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		logger.info("Entering doPost()");
		if (!ServletFileUpload.isMultipartContent(request)) {
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_BAD_REQUEST,
					" Missing Multipart Form Data. ", request.getRequestURL().toString());
			sendErrorMessageResponse(errorMessage, response);
			return;
		}

		try {
			List<FileItem> formItems = upload.parseRequest(request);
			Iterator<FileItem> iter = formItems.iterator();

			// iterates over form's fields
			while (iter.hasNext()) {
				FileItem item = iter.next();

				// processes only fields that are not form fields
				// if (!item.isFormField()) {
				if (!item.isFormField() && item.getFieldName().equals(FORM_FIELD_DATAFILE)) {

					long fileSize = item.getSize();
					if (fileSize < 1) {
						ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_BAD_REQUEST,
								" Missing File Data. ", request.getRequestURL().toString());
						sendErrorMessageResponse(errorMessage, response);
						return;
					}
					// save original uploaded file name
					InputStream inputStream = item.getInputStream();
					String origFileName = item.getName();
					
					DiskFileItemExt itemExt = new DiskFileItemExt(item.getFieldName(),
							item.getContentType(),
							item.isFormField(),
							item.getName(),
							(maxInMemoryFileSizeMb * (int)MB_MULTIPLIER),
							factory.getRepository());
					// Create a temporary unique filename for a file containing the original temp filename plus the real filename containing its file type suffix.
					String tempFilename = itemExt.getTempFile().getName();
					StringBuilder realFileTypeFilename = new StringBuilder(tempFilename);
					realFileTypeFilename.append('-');
					realFileTypeFilename.append(origFileName);
					// create the file in the same temporary directory
					File realInputFile = new File(factory.getRepository(), realFileTypeFilename.toString());
					
					// strip out suffix before saving to ServletRequestListener
					request.setAttribute(TEMP_FILE_NAME_KEY, tempFilename.substring(0, tempFilename.indexOf('.')));

					// turn InputStream into a File in temp directory
					OutputStream outputStream = new FileOutputStream(realInputFile);
					IOUtils.copy(inputStream, outputStream);
					outputStream.close();
					
					try {
						// Send it to the PdfaConverter processor...
						sendPdfaConverterExamineResponse(realInputFile, origFileName, request, response);
					} finally {
						// delete both original temporary file -- if large enough will have been persisted to disk -- and our created file
						if (!item.isInMemory()) { // 
							item.delete();
						}
						if (realInputFile.exists()) {
							realInputFile.delete();
						}
					}

				} else {
					ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_BAD_REQUEST,
							" The request did not have the correct name attribute of \"datafile\" in the form processing. ",
							request.getRequestURL().toString(), " Processing halted.");
					sendErrorMessageResponse(errorMessage, response);
					return;
				}

			}

		} catch (FileUploadException ex) {
			logger.error(ex);
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					" There was an unexpected server error: " + ex.getMessage(), request.getRequestURL().toString(),
					" Processing halted.");
			sendErrorMessageResponse(errorMessage, response);
			return;
		}
	}

	/*
	 * Send input file to converter application and return converted file, if successful, in HttpServletResponse.
	 */
	private void sendPdfaConverterExamineResponse(File inputFile, String inputFileName, HttpServletRequest req, HttpServletResponse resp)
			throws IOException {

		if (!inputFile.exists()) {
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_BAD_REQUEST,
					" File not sent with request: " + inputFileName, " " + req.getRequestURL().toString());
			sendErrorMessageResponse(errorMessage, resp);
			return;
		}

		FileInputStream fileInputStream = null;
		PdfaConverterWrapper pdfaConverterWrapper = null;
		int poolCnt = poolUsageCount++;
		try {
			logger.debug("Borrowing PdfaConverter from pool");
	        logger.info(POOL_MARKER, "About to get PdfaConverter object from pool");
			pdfaConverterWrapper = pdfaConverterWrapperPool.borrowObject();
	        logger.info(POOL_MARKER, "Got PdfaConverter object from pool number: {}", poolCnt);

			logger.debug("Running PdfaConverter on " + inputFile.getPath());
			
			String outputFilenameBase = null;
			// Since a POST creates a temporary file name we need to use original file name.
			if (inputFileName.lastIndexOf('.') > 0) {
				outputFilenameBase = inputFileName.substring(0, inputFileName.indexOf('.'));
			} else {
				outputFilenameBase = inputFileName;
			}
			String generatedPdfFilename = outputFilenameBase + ".pdf";

			// Start the output process
			PdfaConverterOutput output = pdfaConverterWrapper.getPdfaConvert().examine(inputFile, true);
			File pdfReturnFile = output.getPdfaConvertedFile();
			resp.setContentType(PDF_MIMETYPE);
			// Double-quote the filename in case it contains spaces so it doesn't get truncated at first space.
			resp.addHeader("Content-Disposition", "attachment; filename=\"" + generatedPdfFilename+ "\""); // downloaded as attached separate file (This is the filename the browser uses.)
//			resp.addHeader("Content-Disposition", "filename=\"" + generatedPdfFilename+ "\""); // opens in browser window
			resp.addHeader("filename", generatedPdfFilename); // Convenience to get filename without having to parse the "Content-Disposition" line.
			resp.setContentLength((int) pdfReturnFile.length());
			fileInputStream = new FileInputStream(pdfReturnFile);
			OutputStream responseOutputStream = resp.getOutputStream();
			int bytes;
			while ((bytes = fileInputStream.read()) != -1) {
				responseOutputStream.write(bytes);
			}
			logger.debug("Finished writing to OutputStream");
		} catch (UnknownFileTypeException e) {
			// This is user input error of a file type that cannot be handled so 400 error
			logger.warn(e);
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_BAD_REQUEST,
					" PdfaConverter Could not handle this request: " + e.getMessage(), req.getRequestURL().toString(), e.getMessage());
			sendErrorMessageResponse(errorMessage, resp);
		} catch (ExternalToolException | GeneratedFileUnavailableException e) {
			// These cannot be resolved by user so translate to a 500 response
			logger.error(e);
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					" PdfaConverter failed unexpectedly: " + e.getMessage(), req.getRequestURL().toString(), e.getMessage());
			sendErrorMessageResponse(errorMessage, resp);
		} catch (Throwable e) {
			// trap any other type of error
			logger.error("Unexpected exception: " + e.getLocalizedMessage(), e);
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					" PdfaConverter failed unexpectedly: " + e.getMessage(), req.getRequestURL().toString(), e.getMessage());
			sendErrorMessageResponse(errorMessage, resp);
		} finally {
			if (fileInputStream != null) {
				fileInputStream.close();
			}
			if (pdfaConverterWrapper != null) {
				logger.info(POOL_MARKER, "Returning PdfaConverter object to pool number: {}", poolCnt);
				logger.debug("Returning PdfaConverter to pool");
				pdfaConverterWrapperPool.returnObject(pdfaConverterWrapper);
			}
		}
	}

	private void sendPdfaConverterVersionResponse(HttpServletRequest req, HttpServletResponse resp) throws IOException {

		PdfaConverterWrapper pdfaConverterWrapper = null;
		String pdfaConverterVersion = null;
		try {
			logger.debug("Borrowing PdfaConverter from pool");
			pdfaConverterWrapper = pdfaConverterWrapperPool.borrowObject();
			pdfaConverterVersion = pdfaConverterWrapper.getPdfaConvert().getVersion();
		} catch (Exception e) {
			logger.error("Problem executing call...", e);
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					" Getting PdfaConverter version failed: " + e.getMessage(), req.getRequestURL().toString(), e.getMessage());
			sendErrorMessageResponse(errorMessage, resp);
		} finally {
			if (pdfaConverterWrapper != null) {
				logger.debug("Returning PdfaConverter to pool");
				pdfaConverterWrapperPool.returnObject(pdfaConverterWrapper);
			}
		}
		resp.setContentType(TEXT_PLAIN_MIMETYPE);
		PrintWriter out = resp.getWriter();
		out.println(pdfaConverterVersion);
	}

	private void sendErrorMessageResponse(ErrorMessage errorMessage, HttpServletResponse resp) throws IOException {
		String errorMessageStr = errorMessageToString(errorMessage);
		logger.error("Error -- Status:" + errorMessage.getStatusCode() + " - " + errorMessage.getReasonPhrase() + ", "
				+ errorMessage.getMessage());
		PrintWriter out = resp.getWriter();
		resp.setContentType(TEXT_XML_MIMETYPE);
		resp.setStatus(errorMessage.getStatusCode());
		out.println(errorMessageStr);
	}

	private String errorMessageToString(ErrorMessage errorMessage) {
		String errorMessageStr = null;
		try {
			ByteArrayOutputStream outStream = new ByteArrayOutputStream();
			JAXBContext jaxbContext = JAXBContext.newInstance(ErrorMessage.class);
			Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
			jaxbMarshaller.marshal(errorMessage, outStream);
			errorMessageStr = outStream.toString();
		} catch (JAXBException jbe) {
			errorMessageStr = errorMessage.toString();
		}
		return errorMessageStr;
	}
}
