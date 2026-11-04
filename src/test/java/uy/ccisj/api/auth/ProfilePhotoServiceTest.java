package uy.ccisj.api.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class ProfilePhotoServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ProfilePhotoService photos = new ProfilePhotoService(jdbc);

    @Test
    void prefersUploadedPhotosOverGoogleAndUsesGoogleAsFallback() {
        String google = "https://lh3.googleusercontent.com/test";
        assertEquals(google, ProfilePhotoService.photoUrl(null, null, google));
        assertEquals("data:image/png;base64,AQID", ProfilePhotoService.photoUrl(new byte[] {1, 2, 3}, "image/png", google));
        assertNull(ProfilePhotoService.photoUrl(null, null, null));
    }

    @Test
    void uploadsAndRemovesOnlyTheAuthenticatedAccount() {
        byte[] bytes = {1, 2, 3};
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        assertEquals("data:image/png;base64,AQID", photos.upload("account@example.test",
                new MockMultipartFile("file", "photo.png", "image/png", bytes)));
        verify(jdbc).update(contains("WHERE email = ? AND activo = TRUE"), eq(bytes), eq("image/png"),
                eq("account@example.test"));
        photos.remove("account@example.test");
        verify(jdbc).update(contains("foto_perfil = NULL"), eq("account@example.test"));
        assertNull(ProfilePhotoService.photoUrl(null, null));
    }

    @Test
    void rejectsUnsupportedEmptyAndOversizedUploadsWithoutChangingDatabase() {
        for (MockMultipartFile file : new MockMultipartFile[] {
                new MockMultipartFile("file", "photo.svg", "image/svg+xml", new byte[] {1}),
                new MockMultipartFile("file", "photo.png", "image/png", new byte[0]),
                new MockMultipartFile("file", "photo.png", "image/png", new byte[5 * 1024 * 1024 + 1])}) {
            assertEquals(HttpStatus.BAD_REQUEST,
                    assertThrows(ResponseStatusException.class, () -> photos.upload("account@example.test", file)).getStatusCode());
        }
        verifyNoInteractions(jdbc);
    }

    @Test
    void acceptsExactSizeLimitAndAllSupportedTypes() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        for (String type : new String[] {"image/jpeg", "image/png", "image/webp"}) {
            assertTrue(photos.upload("account@example.test",
                    new MockMultipartFile("file", "photo", type, new byte[5 * 1024 * 1024]))
                    .startsWith("data:" + type + ";base64,"));
        }
    }

    @Test
    void reportsMissingAccountsAndUnreadableFiles() throws IOException {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> photos.upload("missing@example.test",
                        new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1}))).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> photos.remove("missing@example.test")).getStatusCode());
        MultipartFile unreadable = mock(MultipartFile.class);
        when(unreadable.getContentType()).thenReturn("image/png");
        when(unreadable.getBytes()).thenThrow(new IOException("read failed"));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> photos.upload("account@example.test", unreadable)).getStatusCode());
    }
}
