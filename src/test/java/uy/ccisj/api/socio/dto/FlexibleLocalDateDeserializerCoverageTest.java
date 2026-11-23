package uy.ccisj.api.socio.dto;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Las fechas de socio aceptan ISO (yyyy-MM-dd) y el formato del navegador en español (dd/MM/yyyy). */
class FlexibleLocalDateDeserializerCoverageTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private SocioPerfilUpdateDTO leer(String fechaJson) throws Exception {
        return mapper.readValue("""
                {"razonSocial":"Empresa","telefono":"099","calle":"Calle","localidad":"San José",
                 "rubroId":1,"fechaAniversario":%s}
                """.formatted(fechaJson), SocioPerfilUpdateDTO.class);
    }

    @Test
    void aceptaFormatoIsoConEspacios() throws Exception {
        assertEquals(LocalDate.of(2024, 2, 29), leer("\" 2024-02-29 \"").fechaAniversario());
    }

    @Test
    void aceptaFormatoDiaMesAnio() throws Exception {
        assertEquals(LocalDate.of(1990, 5, 7), leer("\"07/05/1990\"").fechaAniversario());
    }

    @Test
    void textoVacioOEnBlancoEsNulo() throws Exception {
        assertNull(leer("\"\"").fechaAniversario());
        assertNull(leer("\"   \"").fechaAniversario());
    }

    @Test
    void valorNoTextualSinRepresentacionEsNulo() throws Exception {
        // Un token sin representación textual: getValueAsString() devuelve null.
        var parser = org.mockito.Mockito.mock(com.fasterxml.jackson.core.JsonParser.class);
        var context = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.DeserializationContext.class);
        org.mockito.Mockito.when(parser.getValueAsString()).thenReturn(null);

        assertNull(new FlexibleLocalDateDeserializer().deserialize(parser, context));
        org.mockito.Mockito.verifyNoInteractions(context);
    }

    @Test
    void fechaInvalidaReportaErrorDeEntrada() {
        var error = assertThrows(MismatchedInputException.class, () -> leer("\"31/02/2024x\""));

        assertTrue(error.getMessage().contains("Fecha inválida: 31/02/2024x"));
    }

    @Test
    void socioCreateTambienUsaElDeserializadorFlexible() throws Exception {
        SocioCreateDTO dto = mapper.readValue("""
                {"tipoIdentificador":"CEDULA","identificador":"1234567","email":"a@example.test",
                 "razonSocial":"Empresa","rut":"1","telefono":"099","calle":"C","localidad":"L","rubroId":1,
                 "esDirectivo":true,"fechaAlta":"01/01/2020","fechaAniversario":"2020-01-01"}
                """, SocioCreateDTO.class);

        assertEquals(LocalDate.of(2020, 1, 1), dto.fechaAlta());
        assertEquals(LocalDate.of(2020, 1, 1), dto.fechaAniversario());
        assertEquals(TipoIdentificadorSocio.CEDULA, dto.tipoIdentificador());
        assertTrue(dto.esDirectivo());
    }
}
