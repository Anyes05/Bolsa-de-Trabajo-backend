package uy.ccisj.api.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogoCoverageTest {

    @Test
    void localidadesValidasIgnoranEspaciosYRechazanNulasODesconocidas() {
        assertThat(LocalidadesSanJose.LOCALIDADES).contains("San José de Mayo", "Otra localidad").hasSize(17);
        assertThat(LocalidadesSanJose.isValid("Libertad")).isTrue();
        assertThat(LocalidadesSanJose.isValid("  Ciudad del Plata  ")).isTrue();
        assertThat(LocalidadesSanJose.isValid(null)).isFalse();
        assertThat(LocalidadesSanJose.isValid("Montevideo")).isFalse();
        assertThat(LocalidadesSanJose.isValid("libertad")).isFalse();
    }

    @Test
    void habilidadEstandarGuardaYCambiaSuNombre() {
        HabilidadEstandar habilidad = new HabilidadEstandar("Atención al cliente");
        assertThat(habilidad.getId()).isNull();
        assertThat(habilidad.getNombre()).isEqualTo("Atención al cliente");

        habilidad.setNombre("Liderazgo");
        assertThat(habilidad.getNombre()).isEqualTo("Liderazgo");
        assertThat(new HabilidadEstandar().getNombre()).isNull();
    }

    @Test
    void idiomaGuardaYCambiaSuNombre() {
        Idioma idioma = new Idioma("Inglés");
        assertThat(idioma.getId()).isNull();
        assertThat(idioma.getNombre()).isEqualTo("Inglés");

        idioma.setNombre("Portugués");
        assertThat(idioma.getNombre()).isEqualTo("Portugués");
        assertThat(new Idioma().getNombre()).isNull();
    }

    @Test
    void controladorDeRubrosDelegaCadaOperacionEnElServicio() {
        RubroAdminService service = mock(RubroAdminService.class);
        RubroAdminController controller = new RubroAdminController(service);
        RubroAdminDTO ventas = new RubroAdminDTO(1L, "Ventas", true);
        RubroAdminDTO comercio = new RubroAdminDTO(1L, "Comercio", true);
        RubroAdminDTO inactivo = new RubroAdminDTO(1L, "Comercio", false);
        when(service.list(false)).thenReturn(List.of(ventas));
        when(service.create("Ventas")).thenReturn(ventas);
        when(service.update(1L, "Comercio")).thenReturn(comercio);
        when(service.setActivo(1L, false)).thenReturn(inactivo);

        assertThat(controller.list(false)).containsExactly(ventas);
        assertThat(controller.create(new RubroAdminController.RubroNombreRequest("Ventas"))).isEqualTo(ventas);
        assertThat(controller.update(1L, new RubroAdminController.RubroNombreRequest("Comercio"))).isEqualTo(comercio);
        var activoRequest = new RubroAdminController.RubroActivoRequest(false);
        assertThat(activoRequest.activo()).isFalse();
        assertThat(controller.setActivo(1L, activoRequest)).isEqualTo(inactivo);
        verify(service).list(false);
        verify(service).setActivo(1L, false);
    }
}
