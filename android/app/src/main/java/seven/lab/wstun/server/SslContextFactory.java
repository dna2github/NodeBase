package seven.lab.wstun.server;

import android.content.Context;
import android.util.Log;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

/**
 * Factory for creating SSL context with self-signed certificates.
 */
public class SslContextFactory {
    
    private static final String TAG = "SslContextFactory";
    private static final String KEYSTORE_FILE = "wstun.keystore";
    private static final String KEYSTORE_PASSWORD = "wstunpass";
    private static final String KEY_ALIAS = "wstun";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Get or create SSL context with self-signed certificate.
     */
    public static SslContext getSslContext(Context context) throws Exception {
        File keystoreFile = new File(context.getFilesDir(), KEYSTORE_FILE);
        
        KeyStore keyStore;
        if (!keystoreFile.exists()) {
            Log.i(TAG, "Generating new self-signed certificate");
            keyStore = generateSelfSignedCertificate();
            saveKeyStore(keyStore, keystoreFile);
        } else {
            Log.i(TAG, "Loading existing certificate");
            keyStore = loadKeyStore(keystoreFile);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());

        return SslContextBuilder.forServer(kmf).build();
    }

    /**
     * Generate a new self-signed certificate.
     */
    private static KeyStore generateSelfSignedCertificate() throws Exception {
        // Generate key pair
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        KeyPair keyPair = keyPairGen.generateKeyPair();

        // Certificate validity period
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        Date endDate = new Date(now + 365L * 24 * 60 * 60 * 1000); // 1 year

        // Build certificate
        X500Name issuer = new X500Name("CN=WSTun, O=WSTun, L=Local, C=US");
        BigInteger serial = BigInteger.valueOf(now);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            issuer,
            serial,
            startDate,
            endDate,
            issuer,
            keyPair.getPublic()
        );

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
            .setProvider("BC")
            .build(keyPair.getPrivate());

        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certHolder);

        // Create keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry(
            KEY_ALIAS,
            keyPair.getPrivate(),
            KEYSTORE_PASSWORD.toCharArray(),
            new X509Certificate[]{cert}
        );

        return keyStore;
    }

    /**
     * Save keystore to file.
     */
    private static void saveKeyStore(KeyStore keyStore, File file) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            keyStore.store(fos, KEYSTORE_PASSWORD.toCharArray());
        }
    }

    /**
     * Load keystore from file.
     */
    private static KeyStore loadKeyStore(File file) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(file)) {
            keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
        }
        return keyStore;
    }

    /**
     * Delete existing certificate to regenerate.
     */
    public static void deleteCertificate(Context context) {
        File keystoreFile = new File(context.getFilesDir(), KEYSTORE_FILE);
        if (keystoreFile.exists()) {
            keystoreFile.delete();
        }
    }
}
