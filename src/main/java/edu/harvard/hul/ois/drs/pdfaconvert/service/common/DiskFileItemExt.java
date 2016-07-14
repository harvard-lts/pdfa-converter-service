/*
Copyright (c) 2016 by The President and Fellows of Harvard College
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License. You may obtain a copy of the License at:
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permission and limitations under the License.
*/
package edu.harvard.hul.ois.drs.pdfaconvert.service.common;

import java.io.File;

import org.apache.commons.fileupload.disk.DiskFileItem;

/**
 * This class extends the DiskFileItem class solely to access the protected temporary File
 * it encapsulates. 
 * 
 * @author dan179
 */
public class DiskFileItemExt extends DiskFileItem {

	private static final long serialVersionUID = -1354633482083153614L;

	public DiskFileItemExt(String fieldName, String contentType, boolean isFormField, String fileName,
			int sizeThreshold, File repository) {
		super(fieldName, contentType, isFormField, fileName, sizeThreshold, repository);
	}
	
	/**
	 * Expose publicly the temporary File contained within the parent object.
	 * 
	 * @return The temporary file contained within this object.
	 */
	public File getTempFile() {
		return super.getTempFile();
	}

}
