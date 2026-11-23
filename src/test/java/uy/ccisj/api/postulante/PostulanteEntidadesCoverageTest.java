package uy.ccisj.api.postulante;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uy.ccisj.api.catalogo.HabilidadEstandar;
import uy.ccisj.api.catalogo.Idioma;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.domain.DisponibilidadHoraria;
import uy.ccisj.api.domain.EstadoCivil;
import uy.ccisj.api.domain.Genero;
import uy.ccisj.api.domain.NivelEstudio;
import uy.ccisj.api.domain.NivelIdioma;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;

/** Cubre el mapeo de las entidades JPA del módulo postulante sin necesidad de base de datos. */
class PostulanteEntidadesCoverageTest {

    @Test
    void postulanteGuardaDatosPersonalesRetencionYDemograficos() {
        User usuario = new User("ana@example.test", null, "hash", Role.POSTULANTE);
        Postulante postulante = new Postulante(usuario, "Ana Pérez", "1.234.567-8", "099111222", "San José");

        assertThat(postulante.getId()).isNull();
        assertThat(postulante.getUsuario()).isSameAs(usuario);
        assertThat(postulante.getNombreCompleto()).isEqualTo("Ana Pérez");
        assertThat(postulante.getCedulaIdentidad()).isEqualTo("1.234.567-8");
        assertThat(postulante.getTelefono()).isEqualTo("099111222");
        assertThat(postulante.getZonaResidencia()).isEqualTo("San José");
        assertThat(postulante.isVisible()).isTrue();
        assertThat(postulante.isBusquedaPausada()).isFalse();
        assertThat(postulante.getUltimaActividadAt()).isNotNull();
        assertThat(postulante.getPerfiles()).isEmpty();
        assertThat(postulante.getCvs()).isEmpty();
        assertThat(postulante.getPostulaciones()).isEmpty();

        User otro = new User("otra@example.test", null, "hash", Role.POSTULANTE);
        OffsetDateTime aviso = OffsetDateTime.parse("2026-11-01T10:00:00-03:00");
        postulante.setUsuario(otro);
        postulante.setNombreCompleto("Ana María Pérez");
        postulante.setCedulaIdentidad("2.345.678-9");
        postulante.setTelefono("098000000");
        postulante.setZonaResidencia("Libertad");
        postulante.setVisible(false);
        postulante.setUltimaActividadAt(aviso.minusMonths(3));
        postulante.setRetencionAvisadoAt(aviso);
        postulante.setRetencionDeadlineAt(aviso.plusDays(7));
        postulante.setRetencionToken("token-retencion");
        postulante.setFechaNacimiento(LocalDate.of(1990, 5, 12));
        postulante.setGenero(Genero.FEMENINO);
        postulante.setEstadoCivil(EstadoCivil.CASADO);

        assertThat(postulante.getUsuario()).isSameAs(otro);
        assertThat(postulante.getNombreCompleto()).isEqualTo("Ana María Pérez");
        assertThat(postulante.getCedulaIdentidad()).isEqualTo("2.345.678-9");
        assertThat(postulante.getTelefono()).isEqualTo("098000000");
        assertThat(postulante.getZonaResidencia()).isEqualTo("Libertad");
        assertThat(postulante.isVisible()).isFalse();
        assertThat(postulante.getUltimaActividadAt()).isEqualTo(aviso.minusMonths(3));
        assertThat(postulante.getRetencionAvisadoAt()).isEqualTo(aviso);
        assertThat(postulante.getRetencionDeadlineAt()).isEqualTo(aviso.plusDays(7));
        assertThat(postulante.getRetencionToken()).isEqualTo("token-retencion");
        assertThat(postulante.getFechaNacimiento()).isEqualTo(LocalDate.of(1990, 5, 12));
        assertThat(postulante.getGenero()).isEqualTo(Genero.FEMENINO);
        assertThat(postulante.getEstadoCivil()).isEqualTo(EstadoCivil.CASADO);
        assertThat(new Postulante().getUsuario()).isNull();
    }

    @Test
    void perfilLaboralPermiteEditarTodosSusCampos() {
        Postulante postulante = postulante();
        PerfilLaboral perfil = new PerfilLaboral(postulante, "Ventas", DisponibilidadHoraria.FULL_TIME);

        assertThat(perfil.getId()).isNull();
        assertThat(perfil.getPostulante()).isSameAs(postulante);
        assertThat(perfil.getNombre()).isEqualTo("Ventas");
        assertThat(perfil.getDisponibilidadHoraria()).isEqualTo(DisponibilidadHoraria.FULL_TIME);
        assertThat(perfil.isTieneVehiculo()).isFalse();
        assertThat(perfil.isVisible()).isTrue();
        assertThat(perfil.getRubros()).isEmpty();

        Postulante otro = postulante();
        perfil.setPostulante(otro);
        perfil.setNombre("Administración");
        perfil.setDisponibilidadHoraria(DisponibilidadHoraria.PART_TIME);
        perfil.setTieneVehiculo(true);
        perfil.setVisible(false);
        perfil.setLibreta("Categoría A");
        perfil.setUltimoEmpleo("Cajera");
        perfil.setDescripcionExperiencia("Atención al público");
        perfil.getRubros().add(new RubroEmpleo("Comercio"));

        assertThat(perfil.getPostulante()).isSameAs(otro);
        assertThat(perfil.getNombre()).isEqualTo("Administración");
        assertThat(perfil.getDisponibilidadHoraria()).isEqualTo(DisponibilidadHoraria.PART_TIME);
        assertThat(perfil.isTieneVehiculo()).isTrue();
        assertThat(perfil.isVisible()).isFalse();
        assertThat(perfil.getLibreta()).isEqualTo("Categoría A");
        assertThat(perfil.getUltimoEmpleo()).isEqualTo("Cajera");
        assertThat(perfil.getDescripcionExperiencia()).isEqualTo("Atención al público");
        assertThat(perfil.getRubros()).extracting(RubroEmpleo::getNombreRubro).containsExactly("Comercio");
        assertThat(new PerfilLaboral().getNombre()).isNull();
    }

    @Test
    void cvGuardaMetadatosDelArchivoYColecciones() {
        Postulante postulante = postulante();
        Cv cv = new Cv(postulante);

        assertThat(cv.getId()).isNull();
        assertThat(cv.getPostulante()).isSameAs(postulante);
        assertThat(cv.getVersion()).isEqualTo(1);
        assertThat(cv.isActivo()).isTrue();
        assertThat(cv.getExperiencias()).isEmpty();
        assertThat(cv.getFormaciones()).isEmpty();
        assertThat(cv.getIdiomas()).isEmpty();
        assertThat(cv.getHabilidades()).isEmpty();

        Postulante otro = postulante();
        PerfilLaboral perfil = new PerfilLaboral(otro, "Ventas", DisponibilidadHoraria.FULL_TIME);
        OffsetDateTime carga = OffsetDateTime.parse("2026-11-21T12:00:00-03:00");
        cv.setPostulante(otro);
        cv.setPerfilLaboral(perfil);
        cv.setResumen("Resumen profesional");
        cv.setRutaArchivoCv("postulantes/1/cvs/a.pdf");
        cv.setVersion(3);
        cv.setNombreArchivo("a.pdf");
        cv.setMimeType("application/pdf");
        cv.setSizeBytes(2048L);
        cv.setFechaCarga(carga);
        cv.setActivo(false);
        cv.getHabilidades().add(new HabilidadEstandar("Excel"));

        assertThat(cv.getPostulante()).isSameAs(otro);
        assertThat(cv.getPerfilLaboral()).isSameAs(perfil);
        assertThat(cv.getResumen()).isEqualTo("Resumen profesional");
        assertThat(cv.getRutaArchivoCv()).isEqualTo("postulantes/1/cvs/a.pdf");
        assertThat(cv.getVersion()).isEqualTo(3);
        assertThat(cv.getNombreArchivo()).isEqualTo("a.pdf");
        assertThat(cv.getMimeType()).isEqualTo("application/pdf");
        assertThat(cv.getSizeBytes()).isEqualTo(2048L);
        assertThat(cv.getFechaCarga()).isEqualTo(carga);
        assertThat(cv.isActivo()).isFalse();
        assertThat(cv.getHabilidades()).extracting(HabilidadEstandar::getNombre).containsExactly("Excel");
        assertThat(new Cv().getPostulante()).isNull();
    }

    @Test
    void experienciaLaboralPermiteEditarTodosSusCampos() {
        Cv cv = new Cv(postulante());
        ExperienciaLaboral experiencia = new ExperienciaLaboral(cv, "Tienda SA", "Vendedor", LocalDate.of(2020, 1, 1));

        assertThat(experiencia.getId()).isNull();
        assertThat(experiencia.getCv()).isSameAs(cv);
        assertThat(experiencia.getNombreEmpresa()).isEqualTo("Tienda SA");
        assertThat(experiencia.getCargo()).isEqualTo("Vendedor");
        assertThat(experiencia.getFechaInicio()).isEqualTo(LocalDate.of(2020, 1, 1));

        Cv otro = new Cv(postulante());
        experiencia.setCv(otro);
        experiencia.setNombreEmpresa("Comercio SRL");
        experiencia.setCargo("Encargado");
        experiencia.setFechaInicio(LocalDate.of(2021, 2, 1));
        experiencia.setFechaFin(LocalDate.of(2023, 3, 31));
        experiencia.setTareas("Gestión de stock");
        experiencia.setContactoReferencia("Juan 099000000");

        assertThat(experiencia.getCv()).isSameAs(otro);
        assertThat(experiencia.getNombreEmpresa()).isEqualTo("Comercio SRL");
        assertThat(experiencia.getCargo()).isEqualTo("Encargado");
        assertThat(experiencia.getFechaInicio()).isEqualTo(LocalDate.of(2021, 2, 1));
        assertThat(experiencia.getFechaFin()).isEqualTo(LocalDate.of(2023, 3, 31));
        assertThat(experiencia.getTareas()).isEqualTo("Gestión de stock");
        assertThat(experiencia.getContactoReferencia()).isEqualTo("Juan 099000000");
        assertThat(new ExperienciaLaboral().getCargo()).isNull();
    }

    @Test
    void formacionAcademicaPermiteEditarTodosSusCampos() {
        Cv cv = new Cv(postulante());
        FormacionAcademica formacion = new FormacionAcademica(cv, "UTU", "Técnico", NivelEstudio.EDUCACION_TERCIARIA);

        assertThat(formacion.getId()).isNull();
        assertThat(formacion.getCv()).isSameAs(cv);
        assertThat(formacion.getInstitucion()).isEqualTo("UTU");
        assertThat(formacion.getTitulo()).isEqualTo("Técnico");
        assertThat(formacion.getNivel()).isEqualTo(NivelEstudio.EDUCACION_TERCIARIA);
        assertThat(formacion.isEnCurso()).isFalse();

        Cv otro = new Cv(postulante());
        formacion.setCv(otro);
        formacion.setInstitucion("Liceo 1");
        formacion.setTitulo("Bachillerato");
        formacion.setNivel(NivelEstudio.EDUCACION_MEDIA_SUPERIOR);
        formacion.setFechaInicio(LocalDate.of(2015, 3, 1));
        formacion.setFechaFin(LocalDate.of(2017, 12, 1));
        formacion.setEnCurso(true);

        assertThat(formacion.getCv()).isSameAs(otro);
        assertThat(formacion.getInstitucion()).isEqualTo("Liceo 1");
        assertThat(formacion.getTitulo()).isEqualTo("Bachillerato");
        assertThat(formacion.getNivel()).isEqualTo(NivelEstudio.EDUCACION_MEDIA_SUPERIOR);
        assertThat(formacion.getFechaInicio()).isEqualTo(LocalDate.of(2015, 3, 1));
        assertThat(formacion.getFechaFin()).isEqualTo(LocalDate.of(2017, 12, 1));
        assertThat(formacion.isEnCurso()).isTrue();
        assertThat(new FormacionAcademica().getTitulo()).isNull();
    }

    @Test
    void cvIdiomaArmaLaClaveCompuestaDesdeCvEIdioma() {
        Cv cv = new Cv(postulante());
        ReflectionTestUtils.setField(cv, "id", 10L);
        Idioma idioma = new Idioma("Inglés");
        ReflectionTestUtils.setField(idioma, "id", 3L);

        CvIdioma cvIdioma = new CvIdioma(cv, idioma, NivelIdioma.B2);

        assertThat(cvIdioma.getCv()).isSameAs(cv);
        assertThat(cvIdioma.getIdioma()).isSameAs(idioma);
        assertThat(cvIdioma.getNivel()).isEqualTo(NivelIdioma.B2);
        assertThat(cvIdioma.getId().getCvId()).isEqualTo(10L);
        assertThat(cvIdioma.getId().getIdiomaId()).isEqualTo(3L);
        cvIdioma.setNivel(NivelIdioma.C1);
        assertThat(cvIdioma.getNivel()).isEqualTo(NivelIdioma.C1);

        CvIdioma vacio = new CvIdioma();
        assertThat(vacio.getId()).isNotNull();
        assertThat(vacio.getId().getCvId()).isNull();
        assertThat(vacio.getCv()).isNull();
    }

    @Test
    void cvIdiomaIdComparaPorAmbosComponentes() {
        CvIdioma.CvIdiomaId id = new CvIdioma.CvIdiomaId(1L, 2L);

        assertThat(id).isEqualTo(id);
        assertThat(id).isEqualTo(new CvIdioma.CvIdiomaId(1L, 2L));
        assertThat(id.hashCode()).isEqualTo(new CvIdioma.CvIdiomaId(1L, 2L).hashCode());
        assertThat(id).isNotEqualTo(new CvIdioma.CvIdiomaId(1L, 3L));
        assertThat(id).isNotEqualTo(new CvIdioma.CvIdiomaId(9L, 2L));
        assertThat(id).isNotEqualTo("1-2");
        assertThat(id.equals(null)).isFalse();
        assertThat(new CvIdioma.CvIdiomaId()).isEqualTo(new CvIdioma.CvIdiomaId(null, null));
    }

    @Test
    void registrosDeRespuestaExponenSusComponentes() {
        OffsetDateTime fecha = OffsetDateTime.parse("2026-11-21T12:00:00-03:00");
        CvResponse response = new CvResponse(1L, 2L, "r", "cv.pdf", "application/pdf", 10L, fecha, "https://u");
        assertThat(response.perfilLaboralId()).isEqualTo(2L);
        assertThat(response.downloadUrl()).isEqualTo("https://u");
        assertThat(new CvDownloadResponse("https://d").url()).isEqualTo("https://d");

        var perfilRequest = new PostulantePerfilController.PerfilRequest(
                "Ventas", "FULL_TIME", true, "A", "Cajera", "Exp", false, java.util.List.of("Comercio"));
        assertThat(perfilRequest.rubros()).containsExactly("Comercio");
        var perfilResponse = new PostulantePerfilController.PerfilResponse(
                1L, "Ventas", "FULL_TIME", true, "A", "Cajera", "Exp", true, java.util.List.of());
        assertThat(perfilResponse.visible()).isTrue();
        var datos = new PostulanteDatosPersonalesController.DatosPersonalesRequest("Ana", "1", "2", "3");
        assertThat(datos.zonaResidencia()).isEqualTo("3");
        var datosResponse = new PostulanteDatosPersonalesController.DatosPersonalesResponse(
                "Ana", "1", "2", "3", "a@x", null);
        assertThat(datosResponse.email()).isEqualTo("a@x");
        assertThat(new AdminPostulanteController.ProfileCv(1L, "cv.pdf", "PDF oficial", true, fecha).active()).isTrue();
        assertThat(new AdminPostulanteController.CvLink("https://l").url()).isEqualTo("https://l");
    }

    private static Postulante postulante() {
        return new Postulante(new User("p@example.test", null, "hash", Role.POSTULANTE),
                "Postulante", "1", "099", "San José");
    }
}
