package tv.lunvoryx.player.updater;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class UpdateManagerTest {

    @Test
    public void testCompareVersions() {
        // Newer versions
        assertTrue(UpdateManager.compareVersions("1.0.1", "1.0.0") > 0);
        assertTrue(UpdateManager.compareVersions("1.1.0", "1.0.9") > 0);
        assertTrue(UpdateManager.compareVersions("2.0.0", "1.99.99") > 0);
        assertTrue(UpdateManager.compareVersions("v1.2.0", "1.1.9") > 0);
        assertTrue(UpdateManager.compareVersions("1.0.0.1", "1.0.0") > 0);

        // Same versions
        assertEquals(0, UpdateManager.compareVersions("1.0.0", "1.0.0"));
        assertEquals(0, UpdateManager.compareVersions("v1.0.0", "1.0.0"));
        assertEquals(0, UpdateManager.compareVersions("1.0", "1.0.0"));

        // Older versions (Downgrade attempts)
        assertTrue(UpdateManager.compareVersions("1.0.0", "1.0.1") < 0);
        assertTrue(UpdateManager.compareVersions("0.9.9", "1.0.0") < 0);
        assertTrue(UpdateManager.compareVersions("1.0.0", "2.0.0") < 0);
    }

    @Test
    public void testSafeHttpsUrlValidation() {
        // Valid HTTPS
        assertTrue(UpdateManager.isSafeHttpsUrl("https://api.github.com/repos/david/lunvoryx-player/releases/latest"));
        assertTrue(UpdateManager.isSafeHttpsUrl("https://github.com/user/repo/releases/download/v1.1.0/LunvoryxPlayer-1.1.0.apk"));

        // Insecure or invalid protocols
        assertFalse(UpdateManager.isSafeHttpsUrl("http://insecure-site.com/app.apk"));
        assertFalse(UpdateManager.isSafeHttpsUrl("ftp://files.org/app.apk"));
        assertFalse(UpdateManager.isSafeHttpsUrl("file:///data/local/tmp/app.apk"));
        assertFalse(UpdateManager.isSafeHttpsUrl("content://media/external/app.apk"));
        assertFalse(UpdateManager.isSafeHttpsUrl("javascript:alert(1)"));
        assertFalse(UpdateManager.isSafeHttpsUrl(null));
        assertFalse(UpdateManager.isSafeHttpsUrl(""));
        assertFalse(UpdateManager.isSafeHttpsUrl("https://"));
    }

    @Test
    public void testRepoStringValidation() {
        // Valid repos
        assertTrue(UpdateManager.isValidRepoString("david/lunvoryx-player"));
        assertTrue(UpdateManager.isValidRepoString("My-Org_01/app.tv"));

        // Invalid or malicious injection attempts
        assertFalse(UpdateManager.isValidRepoString("https://evil.com/fake"));
        assertFalse(UpdateManager.isValidRepoString("david"));
        assertFalse(UpdateManager.isValidRepoString("david/repo/extra"));
        assertFalse(UpdateManager.isValidRepoString("user/repo;rm -rf"));
        assertFalse(UpdateManager.isValidRepoString("../escape/repo"));
        assertFalse(UpdateManager.isValidRepoString("user/repo?query=1"));
        assertFalse(UpdateManager.isValidRepoString(""));
        assertFalse(UpdateManager.isValidRepoString(null));
    }

    @Test
    public void testComputeSha256() throws IOException {
        File temp = File.createTempFile("sha_test", ".bin");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write("LunvoryxPlayerSecurityVerification".getBytes(StandardCharsets.UTF_8));
            }

            String hash = UpdateManager.computeSha256(temp);
            assertNotNull(hash);
            assertEquals(64, hash.length());

            // Deterministic verification: verify with different file produces different hash
            File temp2 = File.createTempFile("sha_test2", ".bin");
            try (FileOutputStream out2 = new FileOutputStream(temp2)) {
                out2.write("DifferentContent".getBytes(StandardCharsets.UTF_8));
            }
            String hash2 = UpdateManager.computeSha256(temp2);
            assertNotEquals(hash, hash2);
            temp2.delete();
        } finally {
            temp.delete();
        }
    }

    @Test
    public void testVerifySignaturesMatchNullSafeguards() {
        assertFalse(UpdateManager.verifySignaturesMatch(null, null));
        assertFalse(UpdateManager.verifySignaturesMatch(new android.content.pm.PackageInfo(), null));
        assertFalse(UpdateManager.verifySignaturesMatch(null, new android.content.pm.PackageInfo()));
    }

    @Test
    public void testVerifyDownloadedApkRejectsNullOrEmptyFile() {
        // Non-existent file
        try {
            UpdateManager.verifyDownloadedApk(null, new File("non_existent_file.apk"), null);
            fail("Expected SecurityException for non-existent file");
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains("no existe"));
        }

        // Empty file
        try {
            File emptyFile = File.createTempFile("empty_test", ".apk");
            try {
                UpdateManager.verifyDownloadedApk(null, emptyFile, null);
                fail("Expected SecurityException for empty file");
            } finally {
                emptyFile.delete();
            }
        } catch (Exception e) {
            assertTrue(e instanceof SecurityException);
            assertTrue(e.getMessage().contains("está vacío") || e.getMessage().contains("no existe"));
        }
    }

    @Test
    public void testVerifyDownloadedApkRejectsSha256Mismatch() throws IOException {
        File temp = File.createTempFile("corrupt_test", ".apk");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00}); // ZIP / APK header
            }

            String bogusExpectedHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
            try {
                UpdateManager.verifyDownloadedApk(null, temp, bogusExpectedHash);
                fail("Expected SecurityException for corrupted hash");
            } catch (SecurityException e) {
                assertTrue(e.getMessage().contains("SHA-256 falló"));
            }
        } finally {
            temp.delete();
        }
    }
}
