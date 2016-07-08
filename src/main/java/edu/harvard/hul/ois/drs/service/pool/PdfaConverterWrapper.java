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
