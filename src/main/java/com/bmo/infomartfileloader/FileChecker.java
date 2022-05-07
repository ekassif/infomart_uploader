package com.bmo.infomartfileloader;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.*;
import java.time.LocalDateTime;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

@Component
public class FileChecker {
    private LocalDateTime lastChecked = null;

    WatchService watchService;

    @Scheduled(fixedDelay = Long.MAX_VALUE) // work around to use the WatchService infinite forloop
    public void checkDirectoryForNewFiles() throws Exception{
        watchService = FileSystems.getDefault().newWatchService();
        Path dir = Paths.get("c:/tmp/infomart");
        dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
        while (true){
            WatchKey key = watchService.take();
            System.out.println("");
            for (WatchEvent<?> event: key.pollEvents()){
                System.out.println("GOT A NEW EVENT FROM KEY");
                WatchEvent.Kind kind = event.kind();
                if (kind == StandardWatchEventKinds.ENTRY_CREATE){
                    System.out.println("New File: " + event.context());
                    uploadFile( dir.toString() ,  event.context().toString());
                }
            }
            System.out.println("EXITING INSIDE LOOP LOOP");
            key.reset();
        }
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
