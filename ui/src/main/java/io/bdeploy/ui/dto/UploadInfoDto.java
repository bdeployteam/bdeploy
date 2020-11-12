package io.bdeploy.ui.dto;

import io.bdeploy.common.util.OsHelper.OperatingSystem;

public class UploadInfoDto {

    /** original filename on client's filesystem (filled by client after upload) */
    public String filename;

    /** temp filename on server after upload (filled by server on upload) */
    public String tmpFilename;

    /** flag to indicate that the upload detected a hive structure */
    public boolean isHive;

    /** flag to indicate that the upload detected product information in a non-hive zip (product-info.yaml) */
    public boolean isProduct;

    /** manifest name for generic zip uploads */
    public String name;
    /** manifest tag for generic zip uploads */
    public String tag;
    /** supported operating systems for generic zip uploads (null == all OS) */
    public OperatingSystem supportedOperatingSystems[];

    /** an upload/import result message for UI interaction */
    public String details;
}
