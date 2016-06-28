package edu.harvard.hul.ois.drs.service.servlets;

import static edu.harvard.hul.ois.drs.service.common.Constants.FILE_PARAM;
import static edu.harvard.hul.ois.drs.service.common.Constants.FORM_FIELD_DATAFILE;
import static edu.harvard.hul.ois.drs.service.common.Constants.PDFA_CONVERTER_HOME_SYSTEM_PROP_NAME;
import static edu.harvard.hul.ois.drs.service.common.Constants.RESOURCE_PATH_VERSION;
import static edu.harvard.hul.ois.drs.service.common.Constants.TEXT_HTML_MIMETYPE;
import static edu.harvard.hul.ois.drs.service.common.Constants.TEXT_PLAIN_MIMETYPE;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
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
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.harvard.hul.ois.drs.service.common.ErrorMessage;
import edu.harvard.hul.ois.drs.service.pool.PdfaConverterWrapper;
import edu.harvard.hul.ois.drs.service.pool.PdfaConverterWrapperFactory;
import edu.harvard.hul.ois.drs.service.pool.PdfaConverterWrapperPool;

/**
 * Handles the upload of a file either locally or remotely for processing by
 * Pdfa Converter. For a local upload HTTP GET is used by having a request parameter point
 * to the local file's location. For a remote upload HTTP POST is used to pass
 * the in the file as form data.
 */
public class PdfaConverterServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static String pdfaConverterHome = "";

	private static final String UPLOAD_DIRECTORY = "upload";
	private static final int THRESHOLD_SIZE = 1024 * 1024 * 3; // 3MB
	private static final int MAX_FILE_SIZE = 1024 * 1024 * 40; // 40MB
	private static final int MAX_REQUEST_SIZE = 1024 * 1024 * 50; // 50MB
	private static final Logger logger = LogManager.getLogger();

	private PdfaConverterWrapperPool pdfaConverterWrapperPool;

	public void init() throws ServletException {

		// "pdfaConverter.home" property set differently in Tomcat 7 and JBoss 7.
		// Tomcat: set in catalina.properties
		// JBoss: set as a command line value "-DpdfaConverter.home=<path/to/pdfaConverter/home>
		pdfaConverterHome = System.getProperty(PDFA_CONVERTER_HOME_SYSTEM_PROP_NAME);
		logger.info(PDFA_CONVERTER_HOME_SYSTEM_PROP_NAME + ": " + pdfaConverterHome);

		if (StringUtils.isEmpty(pdfaConverterHome)) {
			logger.fatal(PDFA_CONVERTER_HOME_SYSTEM_PROP_NAME
					+ " system property HAS NOT BEEN SET!!! This web application will not properly run.");
			throw new ServletException(PDFA_CONVERTER_HOME_SYSTEM_PROP_NAME
					+ " system property HAS NOT BEEN SET!!! This web application will not properly run.");
		}

		logger.debug("Initializing PdfaConverter pool");
		GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
		int numObjectsInPool = 5;
		poolConfig.setMinIdle(numObjectsInPool);
		poolConfig.setMaxTotal(numObjectsInPool);
		poolConfig.setTestOnBorrow(true);
		poolConfig.setBlockWhenExhausted(true);
		pdfaConverterWrapperPool = new PdfaConverterWrapperPool(new PdfaConverterWrapperFactory(), poolConfig);
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
		if (RESOURCE_PATH_VERSION.equals(servletPath)) {
			sendPdfaConverterVersionResponse(request, response); // outputs version of
														// PdfaConverter, not the version
														// of web application
			return;
		}

		// Send it to the PdfaConverter processor...
		String filePath = request.getParameter(FILE_PARAM);

		try {
			sendPdfaConverterExamineResponse(filePath, request, response);
		} catch (Exception e) {
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(),
					request.getRequestURL().toString(), e.getMessage());
			sendErrorMessageResponse(errorMessage, response);
		}
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

		// configures upload settings
		DiskFileItemFactory factory = new DiskFileItemFactory();
		factory.setSizeThreshold(THRESHOLD_SIZE);
		factory.setRepository(new File(System.getProperty("java.io.tmpdir")));

		ServletFileUpload upload = new ServletFileUpload(factory);
		upload.setFileSizeMax(MAX_FILE_SIZE);
		upload.setSizeMax(MAX_REQUEST_SIZE);

		// constructs the directory path to store upload file & creates the
		// directory if it does not exist
		String uploadPath = getServletContext().getRealPath("") + File.separator + UPLOAD_DIRECTORY;
		File uploadDir = new File(uploadPath);
		if (!uploadDir.exists()) {
			uploadDir.mkdir();
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
					// ensure a unique local fine name
					String fileSuffix = String.valueOf((new Date()).getTime());
					String fileNameAndPath = uploadPath + File.separator + "pdfaConverter-" + fileSuffix + "-" + item.getName();
					File storeFile = new File(fileNameAndPath);
					item.write(storeFile); // saves the file on disk

					if (!storeFile.exists()) {
						ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
								" Error in upload file.", request.getRequestURL().toString(), " Unspecified.");
						sendErrorMessageResponse(errorMessage, response);
						return;
					}
					// Send it to the PdfaConverter processor...
					try {

						sendPdfaConverterExamineResponse(storeFile.getAbsolutePath(), request, response);

					} catch (Exception e) {
						ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
								e.getMessage(), request.getRequestURL().toString(), e.getMessage());
						sendErrorMessageResponse(errorMessage, response);
						return;
					} finally {
						// delete the uploaded file
						if (storeFile.delete()) {
							logger.debug(storeFile.getName() + " is deleted!");
						} else {
							logger.debug(storeFile.getName() + " could not be deleted!");
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

		} catch (Exception ex) {
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					" There was an unexpected server error. ", request.getRequestURL().toString(),
					" Processing halted.");
			sendErrorMessageResponse(errorMessage, response);
			return;
		}
	}

	private void sendPdfaConverterExamineResponse(String filePath, HttpServletRequest req, HttpServletResponse resp)
			throws IOException {

		if (filePath == null) {
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_BAD_REQUEST,
					" Missing parameter: [" + FILE_PARAM + "] ", req.getRequestURL().toString());
			sendErrorMessageResponse(errorMessage, resp);
			return;
		}

		File file = new File(filePath);
		if (!file.exists()) {
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_BAD_REQUEST,
					" File not sent with request: " + file.getCanonicalPath(), " " + req.getRequestURL().toString());
			sendErrorMessageResponse(errorMessage, resp);
			return;
		}

		PdfaConverterWrapper pdfaConverterWrapper = null;
		try {
			logger.debug("Borrowing PdfaConverter from pool");
			pdfaConverterWrapper = pdfaConverterWrapperPool.borrowObject();

			logger.debug("Running PdfaConverter on " + file.getPath());

			// Start the output process
			ByteArrayOutputStream outStream = new ByteArrayOutputStream();
//			FitsOutput fitsOutput = fitsWrapper.getPdfaConvert().examine(file);
//			fitsOutput.addStandardCombinedFormat();
//			fitsOutput.output(outStream);
			String outputString = outStream.toString();
//			resp.setContentType(TEXT_XML_MIMETYPE);
			PrintWriter out = resp.getWriter();
			out.println(outputString);

		} catch (Exception e) {
			logger.error("Unexpected exception: " + e.getLocalizedMessage(), e);
			ErrorMessage errorMessage = new ErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					" PdfaConverter examine failed", req.getRequestURL().toString(), e.getMessage());
			sendErrorMessageResponse(errorMessage, resp);
		} finally {
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
//			fitsVersion = fitsWrapper.getPdfaConvert().VERSION;
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
		resp.setContentType(TEXT_HTML_MIMETYPE);
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
