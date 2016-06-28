package edu.harvard.hul.ois.drs.service.pool;

import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

//iimport org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

public class PdfaConverterWrapperPool extends GenericObjectPool<PdfaConverterWrapper> {

    public PdfaConverterWrapperPool(PooledObjectFactory<PdfaConverterWrapper> factory, GenericObjectPoolConfig config) {
        super(factory, config);
    }

}

