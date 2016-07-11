/*
Copyright (c) 2016 by The President and Fellows of Harvard College
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License. You may obtain a copy of the License at:
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permission and limitations under the License.
*/
package edu.harvard.hul.ois.drs.service.pool;

import javax.servlet.ServletException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.harvard.hul.ois.drs.pdfaconvert.PdfaConvert;

/**
 * Wrapper around a pdfaConvert instance
 *
 */
public class PdfaConverterWrapper {
	
	private PdfaConvert pdfaConvert;

	private static Logger logger = LogManager.getLogger();

    public PdfaConverterWrapper() throws ServletException {
    	
        logger.debug("Creating new PdfaConverter wrapper");
        try {
            this.pdfaConvert = new PdfaConvert();
		} catch (Throwable t) {
			logger.error("Unexpected Throwable:", t);
        	throw new ServletException("Unexpected Throwable:", t);
		}
        if (pdfaConvert == null){
            logger.error("pdfaConvert is null. Something unexpected happened.");
            throw new ServletException("pdfaConvert is null. Something unexpected initialization happened.");
        }

        logger.debug("Wrapper contains new PdfaConvert instance");
    }

    public PdfaConvert getPdfaConvert(){
        return pdfaConvert;
    }

    public boolean isValid() {
        if (pdfaConvert != null){
            return true;
        } else {
            return false;
        }
    }

}
