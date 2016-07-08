package edu.harvard.hul.ois.drs.clients;

import static edu.harvard.hul.ois.drs.service.common.Constants.FORM_FIELD_DATAFILE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * This is a test client to exercise the PdfaConverterServlet. It requires a configured and running server.
 * 
 * @author dan179
 */
@Ignore // ALWAYS uncomment before saving this class -- This is an integration test!
public class HttpClientIntegrationTest {
	
	private static final String INPUT_FILENAME = "TrivialDocument.docx";
	private static final String LOCAL_TOMCAT_SERVICE_URL = "http://localhost:8080/pdfa-converter-service/convert";
	private static final Logger logger = LogManager.getLogger();
	
	@BeforeClass
	public static void setupClass() {
		logger.debug("in setupClass()");
	}
	
	@Test
	public void doGetTest() throws URISyntaxException {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		URL fileUrl = loader.getResource(INPUT_FILENAME);
		File inputFile = new File(fileUrl.toURI());
		assertNotNull(inputFile);
		assertTrue(inputFile.exists());
		assertTrue(inputFile.isFile());
		assertTrue(inputFile.canRead());
		
		CloseableHttpClient httpclient = HttpClients.createDefault();
		String fileLocation = fileUrl.getPath();
		String url = LOCAL_TOMCAT_SERVICE_URL + "?file=" + fileLocation;
		HttpGet httpGet = new HttpGet(url);
		
		CloseableHttpResponse response = null;
		try {
			logger.debug("executing request " + httpGet.getRequestLine());
			response = httpclient.execute(httpGet);
			StatusLine statusLine = response.getStatusLine();
			logger.debug("Response status line : " + statusLine);
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				long len = entity.getContentLength();
				if (len != -1 && len < 2048) {
					logger.debug("len: " + len);
					logger.debug(EntityUtils.toString(entity));
				} else {
					logger.debug("len: " + len);
					Header[] allHeaders = response.getAllHeaders();
					for (Header h : allHeaders) {
						logger.debug("Header: name:" + h.getName() + " -- value: " + h.getValue() + " -- toString(): " + h);
					}
					Header header = entity.getContentEncoding();
					header = entity.getContentType();
					logger.debug("header content type: " + header.toString());
					header = response.getFirstHeader("filename");
					String filename = header.getValue();
					String savedFilename = filename == null ? "file.pdf" : filename;
					InputStream is = entity.getContent();
					OutputStream out = new FileOutputStream("target/" + savedFilename);
					int bytesCnt;
					while ((bytesCnt = is.read()) != -1) {
						out.write(bytesCnt);
					}
					out.close();
				}
			}
		} catch (IOException e) {
			logger.error("Something went wrong...", e);
			fail(e.getMessage());
		} finally {
			if (response != null) {
				try {
					response.close();
					httpclient.close();
				} catch (IOException e) {
					// nothing to do
					;
				}
			}
		}
		logger.debug("DONE");
	}
	
	@Test
	public void doPutTest() throws URISyntaxException {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		URL fileUrl = loader.getResource(INPUT_FILENAME);
		File inputFile = new File(fileUrl.toURI());
		assertNotNull(inputFile);
		assertTrue(inputFile.exists());
		assertTrue(inputFile.isFile());
		assertTrue(inputFile.canRead());
		
		CloseableHttpClient httpclient = HttpClients.createDefault();
		
		HttpPost httpPost = new HttpPost(LOCAL_TOMCAT_SERVICE_URL);
		FileBody fileContent = new FileBody(inputFile);
		HttpEntity reqEntity = MultipartEntityBuilder.create().addPart(FORM_FIELD_DATAFILE, fileContent).build();
		httpPost.setEntity(reqEntity);
		
		CloseableHttpResponse response = null;
		try {
			logger.debug("executing request " + httpPost.getRequestLine());
			response = httpclient.execute(httpPost);
			StatusLine statusLine = response.getStatusLine();
			logger.debug("Response status line : " + statusLine);
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				long len = entity.getContentLength();
				if (len != -1 && len < 2048) {
					logger.debug("len: " + len);
					logger.debug(EntityUtils.toString(entity));
				} else {
					logger.debug("len: " + len);
					Header[] allHeaders = response.getAllHeaders();
					for (Header h : allHeaders) {
						logger.debug("Header: name:" + h.getName() + " -- value: " + h.getValue() + " -- toString(): " + h);
					}
					Header header = entity.getContentEncoding();
					// logger.debug("header encoding: " + header.toString());
					header = entity.getContentType();
					logger.debug("header content type: " + header.toString());
					header = response.getFirstHeader("filename");
					String filename = header.getValue();
					String savedFilename = filename == null ? "file.pdf" : filename;
					InputStream is = entity.getContent();
					OutputStream out = new FileOutputStream("target/" + savedFilename);
					int bytesCnt;
					while ((bytesCnt = is.read()) != -1) {
						out.write(bytesCnt);
					}
					out.close();
				}
			}
		} catch (IOException e) {
			logger.error("Something went wrong...", e);
			fail(e.getMessage());
		} finally {
			if (response != null) {
				try {
					response.close();
					httpclient.close();
				} catch (IOException e) {
					// nothing to do
					;
				}
			}
		}
		logger.debug("DONE");

	}
	
	@Test
	public void noFileParameterValueTest() throws URISyntaxException {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		URL fileUrl = loader.getResource(INPUT_FILENAME);
		File inputFile = new File(fileUrl.toURI());
		assertNotNull(inputFile);
		assertTrue(inputFile.exists());
		assertTrue(inputFile.isFile());
		assertTrue(inputFile.canRead());
		
		CloseableHttpClient httpclient = HttpClients.createDefault();
		String url = LOCAL_TOMCAT_SERVICE_URL + "?file=";
		HttpGet httpGet = new HttpGet(url);
		
		CloseableHttpResponse response = null;
		try {
			logger.debug("executing request " + httpGet.getRequestLine());
			response = httpclient.execute(httpGet);
			StatusLine statusLine = response.getStatusLine();
			logger.debug("Response status line : " + statusLine);
			assertEquals( 400, statusLine.getStatusCode());
		} catch (IOException e) {
			logger.error("Something went wrong...", e);
			fail(e.getMessage());
		} finally {
			if (response != null) {
				try {
					response.close();
					httpclient.close();
				} catch (IOException e) {
					// nothing to do
					;
				}
			}
		}
		logger.debug("DONE");
	}
	
	@Test
	public void noFileParameterTest() throws URISyntaxException {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		URL fileUrl = loader.getResource(INPUT_FILENAME);
		File inputFile = new File(fileUrl.toURI());
		assertNotNull(inputFile);
		assertTrue(inputFile.exists());
		assertTrue(inputFile.isFile());
		assertTrue(inputFile.canRead());
		
		CloseableHttpClient httpclient = HttpClients.createDefault();
		String url = LOCAL_TOMCAT_SERVICE_URL;
		HttpGet httpGet = new HttpGet(url);
		
		CloseableHttpResponse response = null;
		try {
			logger.debug("executing request " + httpGet.getRequestLine());
			response = httpclient.execute(httpGet);
			StatusLine statusLine = response.getStatusLine();
			logger.debug("Response status line : " + statusLine);
			assertEquals( 400, statusLine.getStatusCode());
		} catch (IOException e) {
			logger.error("Something went wrong...", e);
			fail(e.getMessage());
		} finally {
			if (response != null) {
				try {
					response.close();
					httpclient.close();
				} catch (IOException e) {
					// nothing to do
					;
				}
			}
		}
		logger.debug("DONE");
	}
}
