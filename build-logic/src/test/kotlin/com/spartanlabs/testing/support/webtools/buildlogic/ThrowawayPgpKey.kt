package com.spartanlabs.testing.support.webtools.buildlogic

import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.util.Date

/**
 * A throwaway OpenPGP signing key for one run of the fixtures-publication check, so the nested producer
 * build can sign its publications (vanniktech requires a signatory for a non-snapshot version) and the check
 * can assert an `.asc` beside every published file, the fixtures jar included.
 *
 * The key is generated with BouncyCastle (a `build-logic` test dependency; Gradle's own copy is relocated
 * under `org.gradle.internal.impldep` and is not relied upon) and written only into the producer's generated
 * `gradle.properties` inside the workspace. `ProjectWorkspace.close()` deletes that workspace best-effort only:
 * when a TestKit daemon still holds a file (Windows), the `gradle.properties` holding the key ring and the
 * passphrase can remain under `build-logic/build/testkit/projects/` until `clean`. The key is never logged,
 * passed as a command-line argument or committed.
 */
object ThrowawayPgpKey {

    /**
     * Generates an ASCII-armoured RSA-2048 OpenPGP secret key ring protected by [passphrase], valid for this
     * run only.
     *
     * @param passphrase the passphrase that protects the secret key.
     * @return the armoured secret key ring, or a failure holding the generation error.
     */
    fun generate(passphrase: String): Result<String> = runCatching {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        // This constructor is deprecated in BouncyCastle 1.86 (a compile warning, not an error); it is kept because it
        // is the research's measured helper (plan section 7.6), so the check signs exactly as the probe did.
        val keyPair = JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, generator.generateKeyPair(), Date())
        // SHA-1 is the secret-key checksum digest the OpenPGP format fixes, not the signature hash: the
        // self-certification signs with SHA-256 and the ring is encrypted with AES-256 under the passphrase. The two
        // nulls are the hashed and unhashed signature subpackets (none are needed). The user id's TLD is reserved
        // (RFC 2606), so the key can never name a real address.
        val ring = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION, keyPair, "throwaway <throwaway@example.invalid>",
            JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1), null, null,
            JcaPGPContentSignerBuilder(keyPair.publicKey.algorithm, HashAlgorithmTags.SHA256).setProvider(BouncyCastleProvider()),
            JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256).setProvider(BouncyCastleProvider()).build(passphrase.toCharArray()),
        )
        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { ring.generateSecretKeyRing().encode(it) }
        out.toString(Charsets.US_ASCII)
    }

    /**
     * The armoured key as a one-line `.properties` value: CR removed, each LF written as `\n`.
     *
     * @param armoured the armoured key ring, as [generate] returns it.
     * @return the escaped value.
     */
    fun asPropertyValue(armoured: String): String = armoured.replace("\r", "").replace("\n", "\\n")
}
