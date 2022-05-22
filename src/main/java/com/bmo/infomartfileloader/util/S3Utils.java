package com.bmo.infomartfileloader.util;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.bmo.infomartfileloader.Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class S3Utils {
    Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private Params params;

    private AmazonS3 s3Client;


    private AmazonS3 getS3Client(){
        if (s3Client == null){ // lazy init
            s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(params.getAwsRegionName())
                    .build();
        }

        return s3Client;
    }

    /**
     *
     * @param file The file to upload
     * @param s3key The file name on the s3 bucket (full path key. e.g. /crds/example.zip.pgp)
     */
    public void uploadFile(File file, String s3key) throws Exception {
        //This code expects that you have AWS credentials set up per:
        // https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/setup-credentials.html


        PutObjectRequest request = new PutObjectRequest(params.getS3bucket(), s3key, file);

        // Content type should be detected automatically
//            ObjectMetadata metadata = new ObjectMetadata();
//            metadata.setContentType("plain/text");
//            metadata.addUserMetadata("title", "someTitle");
//            request.setMetadata(metadata);
        getS3Client().putObject(request);

    }

    public boolean exists(String s3key){
        return getS3Client().doesObjectExist(params.getS3bucket(), s3key);
    }
}
