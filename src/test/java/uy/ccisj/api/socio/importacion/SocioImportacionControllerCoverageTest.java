package uy.ccisj.api.socio.importacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;
import uy.ccisj.api.socio.importacion.dto.ImportacionDtos.ImportResponse;
import uy.ccisj.api.socio.importacion.dto.ImportacionDtos.ImportResumen;
import uy.ccisj.api.socio.importacion.dto.ImportacionDtos.PreviewFila;
import uy.ccisj.api.socio.importacion.dto.ImportacionDtos.PreviewResponse;

class SocioImportacionControllerCoverageTest {
    private final SocioImportService service = mock(SocioImportService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SocioImportacionController(service)).build();

    @Test
    void previewDelegaEnElServicioYDevuelveElResumen() throws Exception {
        PreviewFila fila = new PreviewFila(2, "IMPORTABLE", null, "Demo SA", "123456789", "210000000012",
                "099", "demo@demo.com", "Artigas", "1", "Libertad", "Comercio", "2010-01-01", "2018-03-15",
                List.of(CamposFicticios.RUBRO), false);
        when(service.preview(any(MultipartFile.class)))
                .thenReturn(new PreviewResponse(new ImportResumen(1, 1, 1, 0, 0, 0, 0), List.of(fila)));

        mvc.perform(multipart("/admin/socios/importacion/preview").file(archivo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.total").value(1))
                .andExpect(jsonPath("$.resumen.conFicticios").value(1))
                .andExpect(jsonPath("$.filas[0].razonSocial").value("Demo SA"))
                .andExpect(jsonPath("$.filas[0].camposFicticios[0]").value("rubro"));
        verify(service).preview(any(MultipartFile.class));
    }

    @Test
    void importarDelegaEnElServicioYDevuelveLasOmitidas() throws Exception {
        PreviewFila omitida = new PreviewFila(3, "DUPLICADO", "Ya existe", "Dup SA", null, null, null, null,
                null, null, null, null, null, null, List.of(), true);
        when(service.importar(any(MultipartFile.class)))
                .thenReturn(new ImportResponse(new ImportResumen(2, 1, 0, 1, 0, 0, 1), List.of(omitida)));

        mvc.perform(multipart("/admin/socios/importacion").file(archivo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.creados").value(1))
                .andExpect(jsonPath("$.resumen.duplicados").value(1))
                .andExpect(jsonPath("$.omitidas[0].estado").value("DUPLICADO"))
                .andExpect(jsonPath("$.omitidas[0].inactivo").value(true));
    }

    @Test
    void controladorInstanciadoDirectoRetornaLaRespuestaDelServicio() {
        SocioImportacionController controller = new SocioImportacionController(service);
        MultipartFile file = archivo();
        PreviewResponse preview = new PreviewResponse(new ImportResumen(0, 0, 0, 0, 0, 0, 0), List.of());
        ImportResponse importacion = new ImportResponse(new ImportResumen(0, 0, 0, 0, 0, 0, 0), List.of());
        when(service.preview(file)).thenReturn(preview);
        when(service.importar(file)).thenReturn(importacion);

        assertSame(preview, controller.preview(file));
        assertSame(importacion, controller.importar(file));
    }

    // ---------- CamposFicticios ----------

    @Test
    void camposFicticiosParseaYSerializaListasSeparadasPorComa() {
        assertTrue(CamposFicticios.parse(null).isEmpty());
        assertTrue(CamposFicticios.parse("   ").isEmpty());
        assertEquals(Set.of("bps", "rut"), CamposFicticios.parse(" bps , ,rut,bps "));
        assertEquals(List.of("bps", "rut"), CamposFicticios.list("bps, rut,"));
        assertTrue(CamposFicticios.list(null).isEmpty());

        assertNull(CamposFicticios.serialize(null));
        assertNull(CamposFicticios.serialize(Set.of()));
        assertEquals("calle,numero", CamposFicticios.serialize(
                new LinkedHashSet<>(List.of(CamposFicticios.CALLE, CamposFicticios.NUMERO))));

        assertTrue(CamposFicticios.tiene("bps,emailUsuario", CamposFicticios.EMAIL_USUARIO));
        assertFalse(CamposFicticios.tiene("bps", CamposFicticios.EMAIL_CONTACTO));
        assertFalse(CamposFicticios.tiene(null, CamposFicticios.BPS));
    }

    private static MockMultipartFile archivo() {
        return new MockMultipartFile("file", "padron.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1, 2, 3});
    }
}
