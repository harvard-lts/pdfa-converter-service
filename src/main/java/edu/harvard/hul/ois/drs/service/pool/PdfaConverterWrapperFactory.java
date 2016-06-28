package edu.harvard.hul.ois.drs.service.pool;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PdfaConverterWrapperFactory extends BasePooledObjectFactory<PdfaConverterWrapper> {

    private static Logger LOG = LogManager.getLogger();

    @Override
    public PdfaConverterWrapper create() throws Exception {
        LOG.debug("Creating new PdfaConverterWrapper instance in pool");
        return new PdfaConverterWrapper();
    }

    @Override
    public PooledObject<PdfaConverterWrapper> wrap(PdfaConverterWrapper pdfaConverterWrapper){
        return new DefaultPooledObject<PdfaConverterWrapper>(pdfaConverterWrapper);
    }

    @Override
    public boolean validateObject(PooledObject<PdfaConverterWrapper> pdfaConverterWrapper){
        return pdfaConverterWrapper.getObject().isValid();
    }


}