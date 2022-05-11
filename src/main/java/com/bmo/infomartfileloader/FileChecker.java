package com.bmo.infomartfileloader;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;


@Component
public class FileChecker {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private Params params;


//    @Scheduled(fixedDelay = Long.MAX_VALUE) // work around to use the WatchService infinite forloop
    public void checkDirectoryForNewFiles() throws Exception{
        logger.debug("Checking infomart export directory for new exports");

        File dir = new File(params.getExportDirectory());
        if (!dir.exists()){
            logger.error("Export Directory '" + params.getExportDirectory() + "' Does not exist. We cannot scan for new export files");
            return;
        }

        if (!dir.isDirectory()){
            logger.error("Export Directory '" + params.getExportDirectory() + "' is NOT a directory. We cannot scan for new export files");
            return;
        }


        ValueHolder<IOException> exception = ValueHolder.empty();
        File[] files = dir.listFiles(file -> {
            try{
                FileTime ft = Files.getLastModifiedTime(file.toPath());
                return ft.toMillis() > params.getLastExportedFileDatetimeMillis();
            }
            catch(IOException e){
                exception.setValue(e);
                return false;
            }
        });
        if (!exception.isNull()){
            // We encountered and IOException. Log and stop execution
            logger.error("IOException while checking file timestamps");
            return;
        }

        // Sort files by date last modified so the latest one is last in the list
        Arrays.sort(files, (f1, f2) -> {
            try{
                FileTime t1 = Files.getLastModifiedTime(f1.toPath());
                FileTime t2 = Files.getLastModifiedTime(f2.toPath());
                return t1.compareTo(t2);
            }
            catch(IOException e){
                logger.error("Could not get the file time for one of the two files", e);
                return f1.getName().compareTo(f2.getName());
            }
        });

        // Process the files, (zip -> encrypt -> upload to s3)
        Arrays.stream(files).forEach(this::processExportFile);
    }

    @Async
    public void processExportFile(File file){
        logger.debug("Processing file: " + file.getName());
        // 1. Confirm the file is an exported directory with the three expected files within it
        if (!file.isDirectory()){
            logger.warn("File '%s' is not a directory. Skipping...".formatted(file.getName()));
            return;
        }
        else{
            // todo: we will skip this step for now. Fix after with testing.
        }

        // 2. Zip the directory
        File zipFile = new File(params.getTempDir() + file.getName() + ".zip");
        ZipUtil.pack(file, zipFile);

        // 3. Encrypt


        // 4. Push to S3

        // 5. Delete any temp files created
    }

    private void uploadFile(String dir, String file) throws Exception{
        Regions clientRegion = Regions.fromName("us-east-1");
        String bucketName = "infomart-eiad";



        try {
            //This code expects that you have AWS credentials set up per:
            // https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/setup-credentials.html
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(clientRegion)
                    .build();

            // Upload a file as a new object with ContentType and title specified.
            PutObjectRequest request = new PutObjectRequest(bucketName, file, new File(dir + "/" + file));
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("plain/text");
            metadata.addUserMetadata("title", "someTitle");
            request.setMetadata(metadata);
            s3Client.putObject(request);
        } catch (AmazonServiceException e) {
            // The call was transmitted successfully, but Amazon S3 couldn't process
            // it, so it returned an error response.
            e.printStackTrace();
        } catch (SdkClientException e) {
            // Amazon S3 couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon S3.
            e.printStackTrace();
        }
    }
}
