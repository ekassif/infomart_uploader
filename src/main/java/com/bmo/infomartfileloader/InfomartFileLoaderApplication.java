package com.bmo.infomartfileloader;

import com.bmo.infomartfileloader.pgp.PGPUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class InfomartFileLoaderApplication {

    @Autowired
    PGPUtils pgpUtils;

    @Autowired
    Params params;

    public static void main(String[] args) {
        SpringApplication.run(InfomartFileLoaderApplication.class, args);
    }

//    @Value("classpath:pgp/pgp.public")
//    Resource pgpFile;
//
//    @Value("classpath:pgp/sample.txt")
//    Resource sampleFile;
//
//    @Value("classpath:pgp/pgp.private")
//    Resource pgpPrivateFile;
//
//    @PostConstruct
//    public void init() throws IOException, NoSuchProviderException {
//
//        // 2. Zip the directory
//        File zipFile = new File("/Users/eiadkassif/tmp/dummy.zip");
//        ZipUtil.pack(new File("/Users/eiadkassif/tmp/dummy"), zipFile);
//
//        FileOutputStream fos = new FileOutputStream("/Users/eiadkassif/tmp/encrypted");
//
//        // Read PGP Key into string, for testing
//        String pgpKeyString = IOUtils.toString(pgpFile.getInputStream());
//        // Now back into InputStream
//        System.out.println("----");
//        System.out.println(pgpKeyString);
//        System.out.println("----");
//        InputStream publicKeyStream = org.apache.commons.io.IOUtils.toInputStream(pgpKeyString, "utf-8");
//
//
//        OutputStream encryptedStream = pgpUtils.encrypt(publicKeyStream, zipFile, fos);
//        fos.close();
//        fos.flush();
//
//        FileInputStream in = new FileInputStream("/Users/eiadkassif/tmp/encrypted");
//
//        fos = new FileOutputStream("/Users/eiadkassif/tmp/decrypted.zip");
//        pgpUtils.decryptFile(in, pgpPrivateFile.getInputStream(), "admin".toCharArray(), fos);
//        fos.flush();
//        fos.close();
//    }

}
