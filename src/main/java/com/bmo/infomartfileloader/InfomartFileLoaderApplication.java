package com.bmo.infomartfileloader;

import com.amazonaws.util.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.zeroturnaround.zip.ZipUtil;

import javax.annotation.PostConstruct;
import java.io.*;
import java.security.NoSuchProviderException;

@SpringBootApplication
@EnableScheduling
public class InfomartFileLoaderApplication {

    @Autowired
    PGPUtils pgpUtils;
    @Autowired
    Params params;

    public static void main(String[] args) {
        SpringApplication.run(InfomartFileLoaderApplication.class, args);
    }

    @Value("classpath:pgp/pgp.public")
    Resource pgpFile;

    @Value("classpath:pgp/sample.txt")
    Resource sampleFile;

    @Value("classpath:pgp/pgp.private")
    Resource pgpPrivateFile;

    @PostConstruct
    public void init() throws IOException, NoSuchProviderException {
//        String pgpPublicKey = IOUtils.toString(pgpFile.getInputStream());
//        params.setPgpKey(pgpPublicKey);

        // 2. Zip the directory
        File zipFile = new File("/Users/eiadkassif/tmp/dummy.zip");
        ZipUtil.pack(new File("/Users/eiadkassif/tmp/dummy"), zipFile);

        FileOutputStream fos = new FileOutputStream("/Users/eiadkassif/tmp/encrypted");
        OutputStream encryptedStream = pgpUtils.encrypt(pgpFile.getInputStream(), zipFile, fos);
        fos.close();
        fos.flush();

        FileInputStream in = new FileInputStream("/Users/eiadkassif/tmp/encrypted");

        fos = new FileOutputStream("/Users/eiadkassif/tmp/decrypted.zip");
        pgpUtils.decryptFile(in, pgpPrivateFile.getInputStream(), "admin".toCharArray(), fos);
        fos.flush();
        fos.close();
    }

}
