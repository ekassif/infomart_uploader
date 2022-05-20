package com.bmo.infomartfileloader;

import com.bmo.infomartfileloader.pgp.PGPUtils;
import com.bmo.infomartfileloader.util.Results;
import com.bmo.infomartfileloader.util.S3Utils;
import com.bmo.infomartfileloader.util.ValueHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;


@Component
public class InfomartExportFileProcessor {

    // Return codes above zero mean it's ok (e.g. file uploaded already)
    private static final int RC_NOT_DIRECTORY = 10;
    private static final int RC_FILE_ALREADY_UPLOADED = 20;
    private static final int RC_ERROR = -100;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private Params params;

    @Autowired
    private S3Utils s3Utils;

    @Autowired
    private PGPUtils pgpUtils;


    @Scheduled(initialDelay = 60000, fixedDelay = 300000)
    public void run(){
        // 1. Check if  new files have been created in the export directory
        Optional<List<File>> files = checkDirectoryForNewFiles();
        if (files.isPresent()){

        }
    }




    public Optional<List<File>> checkDirectoryForNewFiles(){
        logger.debug("Checking infomart export directory for new exports");

        File dir = new File(params.getExportDirectory());
        if (!dir.exists()){
            logger.error("Export Directory '" + params.getExportDirectory() + "' Does not exist. We cannot scan for new export files");
            return Optional.empty();
        }

        if (!dir.isDirectory()){
            logger.error("Export Directory '" + params.getExportDirectory() + "' is NOT a directory. We cannot scan for new export files");
            return Optional.empty();
        }


        ValueHolder<IOException> exception = ValueHolder.empty();
        File[] files = dir.listFiles(file -> {
            try{
                FileTime ft = Files.getLastModifiedTime(file.toPath());
                return ft.toMillis() >= params.getLastExportedFileDatetimeMillis();
            }
            catch(IOException e){
                exception.setValue(e);
                return false;
            }
        });
        if (!exception.isNull()){
            logger.error("IOException while checking file timestamps", exception.getValue());
            return Optional.empty();
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

        return Optional.of(Arrays.asList(files));
    }

    @Async
    public Future<Results<File>> processExportFile(File file){
        logger.debug("Processing file: " + file.getName());
        // Confirm the file is an exported directory with the three expected files within it
        if (!file.isDirectory()){
            logger.warn("File '%s' is not a directory. Skipping...".formatted(file.getName()));
            return new AsyncResult(Results.builder()
                    .value(file)
                    .returnCode(RC_NOT_DIRECTORY)
                    .errorMessage("File %s is not a directory and will not be processed".formatted(file.getName()))
                    .build());
        }
        else{
            // todo: we will skip this step for now. Confirm directory contents match what we expect in this directory
        }

        String s3FileName = params.getS3prefix();
        s3FileName += file.getName() + ".zip.pgp";

        // Check if this file has already been uploaded to S3.
        try{
            if (s3Utils.exists(s3FileName)){
                return new AsyncResult(Results.builder()
                        .value(file)
                        .errorMessage("File has already been uploaded")
                        .returnCode(RC_FILE_ALREADY_UPLOADED)
                        .build());
            }
        }
        catch (RuntimeException e){
            return new AsyncResult(Results.builder()
                    .value(file)
                    .error(e)
                    .returnCode(RC_ERROR)
                    .build());
        }


        // Zip the directory
        File zipFile = new File(params.getTempDir() + file.getName() + ".zip");
        ZipUtil.pack(file, zipFile);

        // Encrypt
        File encFile = new File(params.getTempDir() + zipFile.getName() + ".pgp");
        try {
            FileOutputStream encOut = new FileOutputStream(encFile);
            pgpUtils.encrypt(zipFile, encOut);
            encOut.flush();
            encOut.close();
        }
        catch(Exception e){

        }


        // Push to S3
        try {
            s3Utils.uploadFile(encFile, s3FileName);
        }
        catch (RuntimeException e){

        }

        // Delete any temp files created
        try{
            zipFile.delete();
            encFile.delete();
        }
        catch(Exception e){

        }

        return new AsyncResult(Results.builder()
                .value(file)
                .returnCode(0)
                .build());
    }


}
