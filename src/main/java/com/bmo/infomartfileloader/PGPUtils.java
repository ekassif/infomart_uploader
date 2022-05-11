package com.bmo.infomartfileloader;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.bouncycastle.util.io.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Iterator;


@Component
public class PGPUtils {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private Params params;

    private PGPPublicKey pgpPublicKey;

    @PostConstruct
    public void init() {
        Security.addProvider(new BouncyCastleProvider());
//        try {
//            PGPPublicKeyRingCollection pgpPublicKeyRings = new PGPPublicKeyRingCollection(
//                    PGPUtil.getDecoderStream(new ByteArrayInputStream(params.getPgpKey().getBytes("utf-8"))),
//                    new JcaKeyFingerprintCalculator());
//            pgpPublicKeyRings.getKeyRings().next().getPublicKey();
//        } catch (IOException e) {
//            logger.error("IOException", e);
//        } catch (PGPException e) {
//            logger.error("PGP Issue", e);
//        }

    }

    public OutputStream encrypt(InputStream pgpFile, File file, OutputStream outputStream){

        try {
            PGPPublicKeyRingCollection pgpPublicKeyRings = new PGPPublicKeyRingCollection(
                    PGPUtil.getDecoderStream(pgpFile),
                    new JcaKeyFingerprintCalculator());
            pgpPublicKey = pgpPublicKeyRings.getKeyRings().next().getPublicKey();
        } catch (IOException e) {
            logger.error("IOException", e);
        } catch (PGPException e) {
            logger.error("PGP Issue", e);
        }

        if (pgpPublicKey == null){
            logger.error("We do not have a PGP Public Key. We cannot encrypt");
            return null;
        }

        try
        {
            OutputStream out = outputStream==null?new ByteArrayOutputStream():outputStream;
            PGPEncryptedDataGenerator   cPk = new PGPEncryptedDataGenerator(new JcePGPDataEncryptorBuilder(PGPEncryptedData.CAST5).setWithIntegrityPacket(true).setSecureRandom(new SecureRandom()).setProvider("BC"));

            cPk.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(pgpPublicKey).setProvider("BC"));

            OutputStream cOut = cPk.open(out, new byte[1 << 16]);


            PGPUtil.writeFileToLiteralData(cOut, PGPLiteralData.BINARY, file, new byte[1 << 16]);
            cOut.close();
            return out;

        }
        catch (PGPException | IOException e)
        {
            logger.error("Could not encrypt", e);
            return null;
        }
    }

//    public OutputStream decrypt(InputStream pgpFile, File inputFile){
//
//        try {
//            PGPPublicKeyRingCollection pgpPublicKeyRings = new PGPPublicKeyRingCollection(
//                    PGPUtil.getDecoderStream(pgpFile),
//                    new JcaKeyFingerprintCalculator());
//            pgpPublicKey = pgpPublicKeyRings.getKeyRings().next().getPublicKey();
//        } catch (IOException e) {
//            logger.error("IOException", e);
//        } catch (PGPException e) {
//            logger.error("PGP Issue", e);
//        }
//
//        if (pgpPublicKey == null){
//            logger.error("We do not have a PGP Public Key. We cannot encrypt");
//            return null;
//        }
//
//        try
//        {
//            OutputStream out = new ByteArrayOutputStream();
//            PGPEncryptedDataGenerator   cPk = new PGPEncryptedDataGenerator(new JcePGPDataEncryptorBuilder(PGPEncryptedData.CAST5).setWithIntegrityPacket(true).setSecureRandom(new SecureRandom()).setProvider("BC"));
//
//            cPk.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(pgpPublicKey).setProvider("BC"));
//
//            OutputStream cOut = cPk.open(out, new byte[1 << 16]);
//
//
//            PGPUtil.writeFileToLiteralData(cOut, PGPLiteralData.BINARY, file, new byte[1 << 16]);
//            cOut.close();
//            return out;
//
//        }
//        catch (PGPException | IOException e)
//        {
//            logger.error("Could not encrypt", e);
//            return null;
//        }
//    }


    /**
     * decrypt the passed in message stream
     */
    public OutputStream decryptFile(
            InputStream in,
            InputStream keyIn,
            char[]      passwd,
            OutputStream outputStream)
            throws IOException, NoSuchProviderException
    {
        in = PGPUtil.getDecoderStream(in);

        try
        {
            JcaPGPObjectFactory pgpF = new JcaPGPObjectFactory(in);
            PGPEncryptedDataList    enc;

            Object                  o = pgpF.nextObject();
            //
            // the first object might be a PGP marker packet.
            //
            if (o instanceof PGPEncryptedDataList)
            {
                enc = (PGPEncryptedDataList)o;
            }
            else
            {
                enc = (PGPEncryptedDataList)pgpF.nextObject();
            }

            //
            // find the secret key
            //
            Iterator it = enc.getEncryptedDataObjects();
            PGPPrivateKey               sKey = null;
            PGPPublicKeyEncryptedData   pbe = null;
            PGPSecretKeyRingCollection  pgpSec = new PGPSecretKeyRingCollection(
                    PGPUtil.getDecoderStream(keyIn), new JcaKeyFingerprintCalculator());

            while (sKey == null && it.hasNext())
            {
                pbe = (PGPPublicKeyEncryptedData)it.next();

                sKey = PGPKeyUtil.findSecretKey(pgpSec, pbe.getKeyID(), passwd);
            }

            if (sKey == null)
            {
                throw new IllegalArgumentException("secret key for message not found.");
            }

            InputStream         clear = pbe.getDataStream(new JcePublicKeyDataDecryptorFactoryBuilder().setProvider("BC").build(sKey));

            JcaPGPObjectFactory    plainFact = new JcaPGPObjectFactory(clear);

            Object message = plainFact.nextObject();

            if (message instanceof PGPCompressedData) {
                PGPCompressedData cData = (PGPCompressedData) plainFact.nextObject();

                InputStream compressedStream = new BufferedInputStream(cData.getDataStream());
                JcaPGPObjectFactory pgpFact = new JcaPGPObjectFactory(compressedStream);

                message = pgpFact.nextObject();
            }

            if (message instanceof PGPLiteralData)
            {
                PGPLiteralData ld = (PGPLiteralData)message;

                InputStream unc = ld.getInputStream();
                OutputStream fOut = outputStream==null?new ByteArrayOutputStream():outputStream;

                Streams.pipeAll(unc, fOut, 8192);

                fOut.close();
                if (pbe.isIntegrityProtected())
                {
                    if (!pbe.verify())
                    {
                        System.err.println("message failed integrity check");
                    }
                    else
                    {
                        System.err.println("message integrity check passed");
                    }
                }
                else
                {
                    System.err.println("no message integrity check");
                }
                return fOut;
            }
            else if (message instanceof PGPOnePassSignatureList)
            {
                throw new PGPException("encrypted message contains a signed message - not literal data.");
            }
            else
            {
                throw new PGPException("message is not a simple encrypted file - type unknown.");
            }


        }
        catch (PGPException e)
        {
            System.err.println(e);
            if (e.getUnderlyingException() != null)
            {
                e.getUnderlyingException().printStackTrace();
            }
            return null;
        }
    }
}
