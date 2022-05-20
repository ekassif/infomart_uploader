package com.bmo.infomartfileloader;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;

/**
 * Provides the parameters to the running process.
 * The parameters can come from SSM Parameter Store or elsewhere.
 */
@Component
public class Params {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    final String PARAM_PATH = "/application/infomart/%s/export/";
    final String PARAM_S3_BUCKET = "s3-bucket";
    final String PARAM_S3_PREFIX = "s3-prefix";
    final String PARAM_EXPORT_DIR = "export-dir";
    final String PARAM_CRDS_PGP_KEY = "crds-pgp-key";
    final String PARAM_TEMP_DIR = "temp-dir";
    final String PARAM_LAST_EXPORT_DATETIME = "last_export_datetime_millis";

    @Getter
    private Long lastExportedFileDatetimeMillis = null;

    @Getter @Value("${infomart.s3bucket:null}")
    private String s3bucket = null;

    @Getter @Value("${infomart.s3prefix:null}")
    private String s3prefix = null;

    @Getter @Value("${infomart.temp_dir:null}")
    private String tempDir = null;

    @Getter @Value("${infomart.export_dir: null}")
    private String exportDirectory = null;

    @Getter @Value("${infomart.pgp_key:null}") @Setter
    private String pgpKey = null;

    @Getter @Value("${infomart.env}")
    String env;

    @Getter @Value("${infomart.aws_region:null}")
    String awsRegionName;

    @Value("${infomart.use_ssm:true}")
    boolean useSSM;

    Region awsRegion;

    @PostConstruct
    public void init(){
        // Get the AWS region
        if (StringUtils.isNotBlank(awsRegionName)){
            awsRegion = RegionUtils.getRegion(awsRegionName);
        }
        else{
            awsRegion = Regions.getCurrentRegion();
        }

        // If region is null, log error and stop scheduled action
        if (awsRegion == null){
            String err = "AWS Region is not available via environment or EC2 metadata. We cannot use the AWS SDK";
            logger.error(err);
            throw new RuntimeException(err); // This should kill the program completely
        }

        if (useSSM) loadParams();
    }

    /**
     * If parameters are stored in SSM, this will UPDATE the SSM params with the latest value
     * @param lastExportedFileDatetimeMillis
     */
    public void setLastExportedFileDatetimeMillis(long lastExportedFileDatetimeMillis){
        this.lastExportedFileDatetimeMillis = lastExportedFileDatetimeMillis;
        // todo: UPDATE SSM PARAMS
    }

    /**
     * This method will go to SSM Parameter store and load the parameters we need and update their values in this bean.
     */
    private void loadParams(){

        logger.debug("Loading SSM parameters");
        AWSSimpleSystemsManagement ssmClient = AWSSimpleSystemsManagementClientBuilder.standard().build();
        GetParametersByPathResult results = ssmClient.getParametersByPath(new GetParametersByPathRequest()
                .withPath(PARAM_PATH.formatted(getEnv()))
                .withWithDecryption(true));
        for (Parameter parm : results.getParameters()){
            String parmName = parm.getName().substring(parm.getName().lastIndexOf('/')+1);
            switch(parmName){
                case PARAM_S3_BUCKET:
                    s3bucket = parm.getValue();
                    logger.debug("Found s3bucket param. Value: " + s3bucket);
                    break;
                case PARAM_S3_PREFIX:
                    s3prefix = parm.getValue();
                    if (!s3prefix.endsWith("/")) s3prefix += "/";
                    logger.debug("Found s3prefix param. Value: " + s3prefix);
                    break;
                case PARAM_CRDS_PGP_KEY:
                    pgpKey = parm.getValue();
                    logger.debug("Found PGP Key param");
                    break;
                case PARAM_EXPORT_DIR:
                    exportDirectory = parm.getValue();
                    logger.debug("Found export dir param. Value: " + exportDirectory);
                    break;
                case PARAM_LAST_EXPORT_DATETIME:
                    lastExportedFileDatetimeMillis = Long.parseLong(parm.getValue());
                    logger.debug("Found last exported file value: " + new Date(lastExportedFileDatetimeMillis));
                    break;
                case PARAM_TEMP_DIR:
                    tempDir = parm.getValue();
                    if (!tempDir.endsWith("/")) tempDir += "/";
                    logger.debug("Found the temp dir to store working files in: " + tempDir);
                    break;
            }
        }
    }
}
