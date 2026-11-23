package uy.ccisj.api.postulante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

/** Controladores delgados del módulo postulante: delegan en los servicios con el usuario autenticado. */
class PostulanteControladoresCoverageTest {
    private static final String EMAIL = "persona@example.test";
    private final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(EMAIL, null);

    @Test
    void cvControllerListaSubeEliminaYDescargaConElEmailAutenticado() throws Exception {
        CvService service = mock(CvService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PostulanteCvController(service)).build();
        CvResponse cv = new CvResponse(5L, 2L, "Resumen", "cv.pdf", "application/pdf", 10L,
                OffsetDateTime.parse("2026-11-21T12:00:00-03:00"), "https://descarga/cv");
        when(service.listMyCvs(EMAIL)).thenReturn(List.of(cv));
        when(service.uploadCv(eq(EMAIL), any(MultipartFile.class), eq("Resumen"), eq(2L), eq(true))).thenReturn(cv);
        when(service.downloadLink(EMAIL, 5L)).thenReturn(new CvDownloadResponse("https://descarga/cv"));

        mvc.perform(get("/postulante/cvs").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].nombreArchivo").value("cv.pdf"));
        mvc.perform(multipart("/postulante/cvs")
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF".getBytes()))
                        .param("resumen", "Resumen").param("perfilLaboralId", "2").param("reemplazar", "true")
                        .principal(auth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfilLaboralId").value(2));
        mvc.perform(delete("/postulante/cvs/5").principal(auth)).andExpect(status().isNoContent());
        mvc.perform(get("/postulante/cvs/5/download").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://descarga/cv"));

        verify(service).deleteCv(EMAIL, 5L);
    }

    @Test
    void cvControllerPorDefectoNoReemplaza() {
        CvService service = mock(CvService.class);
        PostulanteCvController controller = new PostulanteCvController(service);
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF".getBytes());

        controller.uploadCv(auth, file, null, null, false);

        verify(service).uploadCv(EMAIL, file, null, null, false);
    }

    @Test
    void permanenciaControllerDevuelveEstadoYConfirma() {
        RetencionPermanenciaService service = mock(RetencionPermanenciaService.class);
        PostulantePermanenciaController controller = new PostulantePermanenciaController(service);
        var pendiente = new RetencionPermanenciaService.PermanenciaEstado(true, true, false, OffsetDateTime.now());
        var confirmado = new RetencionPermanenciaService.PermanenciaEstado(false, false, false, null);
        when(service.estado(EMAIL)).thenReturn(pendiente, confirmado);

        assertThat(controller.estado(auth)).isSameAs(pendiente);
        assertThat(controller.confirmar(auth)).isSameAs(confirmado);
        verify(service).confirmarAutenticado(EMAIL);
    }

    @Test
    void adminRetencionEjecutaYFuerzaInactividad() {
        RetencionPermanenciaService service = mock(RetencionPermanenciaService.class);
        AdminRetencionController controller = new AdminRetencionController(service);
        var resultado = new RetencionPermanenciaService.RetencionRunResult(2, 1, 1, 3);
        when(service.procesar()).thenReturn(resultado);

        assertThat(controller.ejecutar()).isEqualTo(resultado);
        controller.forzarInactividad(9L);

        verify(service).forzarInactividad(9L);
    }

    @Test
    void schedulerProcesaConYSinNovedadesYToleraFallos() {
        RetencionPermanenciaService service = mock(RetencionPermanenciaService.class);
        RetencionPermanenciaScheduler scheduler = new RetencionPermanenciaScheduler(service);
        when(service.procesar())
                .thenReturn(new RetencionPermanenciaService.RetencionRunResult(0, 0, 0, 0))
                .thenReturn(new RetencionPermanenciaService.RetencionRunResult(1, 1, 0, 0))
                .thenReturn(new RetencionPermanenciaService.RetencionRunResult(0, 0, 0, 2))
                .thenThrow(new IllegalStateException("BD caída"));

        scheduler.procesarRetencionDiaria();
        scheduler.procesarRetencionDiaria();
        scheduler.procesarRetencionDiaria();
        // El fallo se registra y no se propaga al hilo del scheduler.
        scheduler.procesarRetencionDiaria();

        verify(service, org.mockito.Mockito.times(4)).procesar();
    }

    @Test
    void schedulerNoPropagaExcepcionesDelServicio() {
        RetencionPermanenciaService service = mock(RetencionPermanenciaService.class);
        doThrow(new RuntimeException("falla")).when(service).procesar();

        new RetencionPermanenciaScheduler(service).procesarRetencionDiaria();

        verify(service).procesar();
    }
}
