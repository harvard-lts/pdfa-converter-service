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

import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.FILE_PARAM;
import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.FORM_FIELD_DATAFILE;
import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.PDF_MIMETYPE;
import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.RESOURCE_PATH_VERSION;
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

import javax.servlet.ServletException;
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

import edu.harvard.hul.ois.drs.pdfaconvert.ExternalToolException;
import edu.harvard.hul.ois.drs.pdfaconvert.GeneratedFileUnavailableException;
import edu.harvard.hul.ois.drs.pdfaconvert.PdfaConverterOutput;
import edu.harvard.hul.ois.drs.pdfaconvert.UnknownFileTypeException;
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
public class PdfaConverterServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static final String UPLOAD_DIRECTORY = "upload";
	private static final int THRESHOLD_SIZE = 1024 * 1024 * 3; // 3MB
	private static final int MAX_FILE_SIZE = 1024 * 1024 * 40; // 40MB
	private static final int MAX_REQUEST_SIZE = 1024 * 1024 * 50; // 50MB
	private static final Logger logger = LogManager.getLogger();

	private PdfaConverterWrapperPool pdfaConverterWrapperPool;
	private DiskFileItemFactory factory;

	public void init() throws ServletException {

		logger.debug("Initializing PdfaConverter pool");
		GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
		int numObjectsInPool = 5;
		poolConfig.setMinIdle(numObjectsInPool);
		poolConfig.setMaxTotal(numObjectsInPool);
		poolConfig.setTestOnBorrow(true);
		poolConfig.setBlockWhenExhausted(true);
		pdfaConverterWrapperPool = new PdfaConverterWrapperPool(new PdfaConverterWrapperFactory(), poolConfig);

		// configures upload settings
		factory = new DiskFileItemFactory();
		factory.setSizeThreshold(THRESHOLD_SIZE);
		factory.setRepository(new File(System.getProperty("java.io.tmpdir")));

		ServletFileUpload upload = new ServletFileUpload(factory);
		upload.setFileSizeMax(MAX_FILE_SIZE);
		upload.setSizeMax(MAX_REQUEST_SIZE);

		logger.debug("PdfaConverter pool finished Initializing");
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

		// not sure if this can be a member variable; whether it's thread safe
		ServletFileUpload upload = new ServletFileUpload(factory);
		upload.setFileSizeMax(MAX_FILE_SIZE);
		upload.setSizeMax(MAX_REQUEST_SIZE);

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

					// turn InputStream into a File
					File file = new File(origFileName);
					OutputStream outputStream = new FileOutputStream(file);
					IOUtils.copy(inputStream, outputStream);
					outputStream.close();

					// Send it to the PdfaConverter processor...
					sendPdfaConverterExamineResponse(file, origFileName, request, response);

				} else {
					ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_BAD_REQUEST,
							" The request did not have the correct name attribute of \"datafile\" in the form processing. ",
							request.getRequestURL().toString(), " Processing halted.");
					sendErrorMessageResponse(errorMessage, response);
					return;
				}

			}

		} catch (FileUploadException ex) {
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					" There was an unexpected server error. ", request.getRequestURL().toString(),
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
		try {
			logger.debug("Borrowing PdfaConverter from pool");
			pdfaConverterWrapper = pdfaConverterWrapperPool.borrowObject();

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
			PdfaConverterOutput output = pdfaConverterWrapper.getPdfaConvert().examine(inputFile);
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
		} catch (UnknownFileTypeException e) {
			// This is user input error of a file type that cannot be handled so 400 error
			logger.warn(e);
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_BAD_REQUEST,
					" PdfaConverter Could not handle this request - ", req.getRequestURL().toString(), e.getMessage());
			sendErrorMessageResponse(errorMessage, resp);
		} catch (ExternalToolException | GeneratedFileUnavailableException e) {
			// These cannot be resolved by user so translate to a 500 response
			logger.error(e);
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					" PdfaConverter failed unexpectedly - ", req.getRequestURL().toString(), e.getMessage());
			sendErrorMessageResponse(errorMessage, resp);
		} catch (Throwable e) {
			// trap any other type of error
			logger.error("Unexpected exception: " + e.getLocalizedMessage(), e);
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					" PdfaConverter failed unexpectedly - ", req.getRequestURL().toString(), e.getMessage());
			sendErrorMessageResponse(errorMessage, resp);
		} finally {
			if (fileInputStream != null) {
				fileInputStream.close();
			}
			if (pdfaConverterWrapper != null) {
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
					" Getting PdfaConverter version failed", req.getRequestURL().toString(), e.getMessage());
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
