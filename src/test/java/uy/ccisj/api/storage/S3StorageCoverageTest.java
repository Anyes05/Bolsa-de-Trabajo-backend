package uy.ccisj.api.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * S3StorageService con S3Client y S3Presigner simulados: nunca se habla con AWS real.
 */
class S3StorageCoverageTest {
    private final S3Client s3 = mock(S3Client.class);
    private final S3Presigner presigner = mock(S3Presigner.class);
    private final S3StorageService storage = new S3StorageService(s3, presigner, "bucket-de-prueba");

    @Test
    void subeElArchivoConBucketClaveYTipo() throws IOException {
        MockMultipartFile archivo = new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.7".getBytes());

        storage.putObject("postulantes/1/cvs/a.pdf", archivo);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(request.capture(), body.capture());
        assertEquals("bucket-de-prueba", request.getValue().bucket());
        assertEquals("postulantes/1/cvs/a.pdf", request.getValue().key());
        assertEquals("application/pdf", request.getValue().contentType());
        assertEquals(8L, body.getValue().optionalContentLength().orElseThrow());
    }

    @Test
    void archivoIlegibleSeInformaComoErrorDeEstado() throws IOException {
        MultipartFile archivo = mock(MultipartFile.class);
        when(archivo.getContentType()).thenReturn("application/pdf");
        when(archivo.getInputStream()).thenThrow(new IOException("disco"));

        var error = assertThrows(IllegalStateException.class, () -> storage.putObject("clave", archivo));

        assertEquals("No se pudo leer el archivo para cargarlo a S3", error.getMessage());
        assertInstanceOf(IOException.class, error.getCause());
        verifyNoInteractions(s3);
    }

    @Test
    void borraElObjetoDelBucket() {
        storage.deleteObject("postulantes/1/cvs/a.pdf");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(request.capture());
        assertEquals("bucket-de-prueba", request.getValue().bucket());
        assertEquals("postulantes/1/cvs/a.pdf", request.getValue().key());
    }

    @Test
    void generaUrlPrefirmadaConLaDuracionPedida() throws Exception {
        PresignedGetObjectRequest firmado = mock(PresignedGetObjectRequest.class);
        URL url = URI.create("https://bucket-de-prueba.s3.amazonaws.com/a.pdf?X-Amz-Signature=abc").toURL();
        when(firmado.url()).thenReturn(url);
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(firmado);

        String resultado = storage.presignedDownloadUrl("a.pdf", Duration.ofMinutes(15));

        assertEquals("https://bucket-de-prueba.s3.amazonaws.com/a.pdf?X-Amz-Signature=abc", resultado);
        ArgumentCaptor<GetObjectPresignRequest> request = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(request.capture());
        assertEquals(Duration.ofMinutes(15), request.getValue().signatureDuration());
        assertEquals("bucket-de-prueba", request.getValue().getObjectRequest().bucket());
        assertEquals("a.pdf", request.getValue().getObjectRequest().key());
    }

    @Test
    void sinBucketConfiguradoNingunaOperacionTocaS3() {
        for (String bucket : new String[] {null, "", "   "}) {
            S3StorageService sinBucket = new S3StorageService(s3, presigner, bucket);
            MultipartFile archivo = new MockMultipartFile("file", new byte[] {1});
            assertEquals("No se configuro app.storage.bucket para el backend",
                    assertThrows(IllegalStateException.class, () -> sinBucket.putObject("k", archivo)).getMessage());
            assertThrows(IllegalStateException.class, () -> sinBucket.deleteObject("k"));
            assertThrows(IllegalStateException.class, () -> sinBucket.presignedDownloadUrl("k", Duration.ofMinutes(1)));
        }
        verifyNoInteractions(s3, presigner);
    }

    @Test
    void laConfiguracionCreaClientesEnLaRegionIndicada() {
        S3StorageConfig config = new S3StorageConfig();
        try (S3Client client = config.s3Client("sa-east-1");
                S3Presigner firmador = config.s3Presigner("sa-east-1")) {
            assertNotNull(client);
            assertNotNull(firmador);
            assertSame(Region.SA_EAST_1, client.serviceClientConfiguration().region());
        }
    }
}
