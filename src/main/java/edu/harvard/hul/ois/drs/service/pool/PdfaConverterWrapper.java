package edu.harvard.hul.ois.drs.service.pool;

import static edu.harvard.hul.ois.drs.service.common.Constants.PDFA_CONVERTER_HOME_SYSTEM_PROP_NAME;

import javax.servlet.ServletException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.harvard.hul.ois.drs.pdfaconvert.PdfaConvert;

/**
 * Wrapper around a pdfaConvert instance
 *
 */
public class PdfaConverterWrapper {
	

    private static final String pdfaConverterHome = System.getProperty(PDFA_CONVERTER_HOME_SYSTEM_PROP_NAME);    
    private static Logger logger = LogManager.getLogger();
    private PdfaConvert pdfaConvert;

    public PdfaConverterWrapper() throws ServletException {
    	
        logger.debug("Creating new PdfaConverter wrapper");
        logger.info("PdfaConverter HOME: "+pdfaConverterHome);
        
        // This really should have been checked earlier.
        if (pdfaConverterHome == null) {
        	logger.fatal(PDFA_CONVERTER_HOME_SYSTEM_PROP_NAME + " system property HAS NOT BEEN SET!!! This web application will not properly run.");
        	throw new ServletException(PDFA_CONVERTER_HOME_SYSTEM_PROP_NAME + " system property HAS NOT BEEN SET!!! This web application will not properly run.");
        }
        
        try {
            this.pdfaConvert = new PdfaConvert(pdfaConverterHome);
//        } catch (FitsException fce){
//            logger.error("Error initializing FITS " + fce.getMessage());
//        	throw new ServletException("Error initializing FITS ", fce);
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
