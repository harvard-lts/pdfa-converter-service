/*
Copyright (c) 2016 by The President and Fellows of Harvard College
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License. You may obtain a copy of the License at:
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permission and limitations under the License.
*/
package edu.harvard.hul.ois.drs.pdfaconvert.service.listeners;

import static edu.harvard.hul.ois.drs.pdfaconvert.service.common.Constants.TEMP_FILE_NAME_KEY;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.harvard.hul.ois.drs.pdfaconvert.PdfaConvert;

/**
 * Request listener class for performing clean-up on the derivative PDF file converted from original input file.
 * 
 * @author dan179
 */
@WebListener
public class PdfaRequestListener implements ServletRequestListener {

	private PdfaConvert pdfaConverter;
	private static final Logger logger = LogManager.getLogger();

	public PdfaRequestListener() {
		logger.debug("In PdfaRequestListener c-tor");
		pdfaConverter = new PdfaConvert();
	}

	/**
	 * Attempt to delete the converted file stored in the configured output directory of the PDF/A Utility.
	 * The hard dependency here is that the temporary file name (without the file-type suffix) stored by the
	 * Servlet in the Request must match the derivative file name created by the PDF/A utility which is
	 * placed in the configured output directory. Each conversion tool in the PDF/A utility is responsible for
	 * creating this derivative PDF file with the same temporary file name (plus .pdf suffix) and placing in the
	 * configured output directory.
	 * 
	 * @see javax.servlet.ServletRequestListener#requestDestroyed(javax.servlet.ServletRequestEvent)
	 */
	@Override
	public void requestDestroyed(ServletRequestEvent sre) {
		logger.debug("requestDestroyed");
		String filename = (String) sre.getServletRequest().getAttribute(TEMP_FILE_NAME_KEY);
		logger.debug("filename: {}", filename);
		// Since every request goes through this method, only try to delete if the reqest was to actually convert a document.
		if (filename != null) {
			String fullFilename = filename + ".pdf";
			// make call to utility to delete the file
			boolean wasDeleted = pdfaConverter.deleteConvertedFile(fullFilename);
			if (wasDeleted) {
				logger.debug("File was deleted: {}", filename );
			} else {
				logger.warn("File NOT deleted: {}", filename );
			}
		}
	}

	@Override
	public void requestInitialized(ServletRequestEvent sre) {
		// no-op
	}
}