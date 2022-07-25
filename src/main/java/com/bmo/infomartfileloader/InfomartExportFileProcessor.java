package com.bmo.infomartfileloader;

import com.bmo.infomartfileloader.pgp.PGPUtils;
import com.bmo.infomartfileloader.util.Results;
import com.bmo.infomartfileloader.util.S3Utils;
import com.bmo.infomartfileloader.util.ValueHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class InfomartExportFileProcessor {

    // Return codes above zero mean it's ok (e.g. file uploaded already)
    private static final int RC_NOT_DIRECTORY = 10;
    private static final int RC_FILE_NAME_PATTERN_MISMATCH = 11;
    private static final int RC_FILE_ALREADY_UPLOADED = 20;
    private static final int RC_ERROR = -100;
    private static final int RC_WARNING = 100;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private Params params;

    @Autowired
    private S3Utils s3Utils;

    @Autowired
    private PGPUtils pgpUtils;

    Pattern fileNamePattern = Pattern.compile("export_(\\d+)_(\\d{4})_(\\d\\d)_(\\d\\d)_(\\d\\d)_(\\d\\d)_(\\d\\d)");
    String exportFileNameTemplate = "INFOMART-CAD.HRLY_NACCC_ANALYTICSDATA_%s_%s";

    @Scheduled(initialDelay = 30000, fixedDelay = 30000)
    public void run(){
        // Check if  new files have been created in the export directory
        Optional<List<File>> files = checkDirectoryForNewFiles();
        if (files.isPresent() && !files.get().isEmpty()){
            List<Results<File>> results = files.get().parallelStream().map(this::processExportFile).collect(Collectors.toList());
            ValueHolder<Long> lastExportedTime = new ValueHolder<>(0L);
            ValueHolder<Boolean> failure = new ValueHolder<>(false);
            results.forEach(r -> {
                if (r.isOK()){
                    if (!failure.getValue()){ // only update if everything so far succeeded
                        try{
                            lastExportedTime.setValue(Files.getLastModifiedTime(r.getValue().toPath()).toMillis());
                        }
                        catch(IOException e){
                            logger.error("Could not get last modification time for file: " + r.getValue().getPath());
                        }
                    }
                }
                else if (r.getReturnCode() < 0) { // error
                   logger.error(r.getErrorMessage(), r.getError());
                   failure.setValue(true);
                }
                else{ // warning
                    logger.warn(r.getErrorMessage());
                }
            });

            if (lastExportedTime.getValue() > 0){
                try {
                    params.setLastExportedFileDatetimeMillis(lastExportedTime.getValue());
                }
                catch(RuntimeException e){
                    logger.error("Error while saving 'lastDatetime' for files processed", e);
                }
            }
        }
        else{
            logger.info("No new export files to process");
        }
    }

    public Optional<List<File>> checkDirectoryForNewFiles(){
        logger.info("Checking infomart export directory for new exports");

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

    public Results<File> processExportFile(File file){
        logger.debug("Processing file: " + file.getName());
        // Confirm the file is an exported directory with the three expected files within it

        String cadFileName = null;
        if (!file.isDirectory()){
            logger.warn(String.format("File '%s' is not a directory. Skipping...", file.getName()));
            return Results.<File>builder()
                    .value(file)
                    .returnCode(RC_NOT_DIRECTORY)
                    .errorMessage(String.format("File %s is not a directory and will not be processed", file.getName()))
                    .build();
        }
        else{
            Matcher matcher = fileNamePattern.matcher(file.getName());
            if (!matcher.matches()){
                logger.warn("File name does not match pattern we are looking for. FileName: " + file.getName());
                return Results.<File>builder()
                        .value(file)
                        .returnCode(RC_FILE_NAME_PATTERN_MISMATCH)
                        .errorMessage("File name does not match pattern we are looking for. FileName: " + file.getName())
                        .build();
            }

            String fileDateTime = matcher.group(2) + matcher.group(3) + matcher.group(4) + matcher.group(5) + matcher.group(6) + matcher.group(7);
            cadFileName = String.format(exportFileNameTemplate, matcher.group(1), fileDateTime);
        }

        String s3CrdsFileName = params.getS3prefixCrds();
        s3CrdsFileName += cadFileName + ".zip.pgp";

        String s3InfomartFileName = params.getS3PrefixInfomart() + file.getName() + ".zip";

        // Check if this file has already been uploaded to S3.
        try{
            if (s3Utils.exists(s3CrdsFileName)){
                return Results.<File>builder()
                        .value(file)
                        .errorMessage("File has already been uploaded")
                        .returnCode(RC_FILE_ALREADY_UPLOADED)
                        .build();
            }
        }
        catch (RuntimeException e){
            return Results.<File>builder()
                    .value(file)
                    .error(e)
                    .returnCode(RC_ERROR)
                    .build();
        }

        // Make sure temp dir exists or create it
        File tempDir = new File(params.getTempDir());
        if (!tempDir.exists()) tempDir.mkdirs();

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
            return Results.<File>builder()
                    .returnCode(RC_ERROR)
                    .value(file)
                    .errorMessage("Could not encrypt file")
                    .error(e)
                    .build();
        }


        // Push to S3
        try {
            s3Utils.uploadFile(zipFile, s3InfomartFileName);
            s3Utils.uploadFile(encFile, s3CrdsFileName);
        }
        catch (Exception e){
            return Results.<File>builder()
                    .returnCode(RC_ERROR)
                    .value(file)
                    .errorMessage("Could not upload file to s3")
                    .error(e)
                    .build();
        }

        // Delete any temp files created
        try{
            zipFile.delete();
            encFile.delete();
        }
        catch(Exception e){
            return Results.<File>builder()
                    .returnCode(RC_WARNING)
                    .value(file)
                    .errorMessage("Could not delete temp files")
                    .error(e)
                    .build();
        }

        return Results.<File>builder()
                .value(file)
                .returnCode(0)
                .build();
    }


}
