package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import uy.ccisj.api.domain.MetodoPago;
import uy.ccisj.api.socio.importacion.dto.HistorialCajaImportacionDtos.ImportResponse;
import uy.ccisj.api.socio.importacion.dto.HistorialCajaImportacionDtos.PreviewResponse;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;

@ExtendWith(MockitoExtension.class)
class HistorialCajaImportacionServiceTest {
    @Mock private SocioRepository socioRepository;
    @Mock private CuotaRepository cuotaRepository;
    @Mock private PagoRepository pagoRepository;
    @Mock private CajaService cajaService;
    @Mock private EntityManager entityManager;
    @Mock private Query query;
    @InjectMocks private HistorialCajaImportacionService service;

    private final Socio socio = new Socio(new User("test@example.test", "123456789", "hash", Role.SOCIO), "Socio de prueba");

    @Test
    void previsualizaUnaCuotaPagadaYDetectaPeriodosExistentes() throws Exception {
        when(socioRepository.findById(7L)).thenReturn(Optional.of(socio));
        when(cuotaRepository.existsBySocioIdAndPeriodo(7L, LocalDate.of(2024, 3, 1))).thenReturn(true);

        PreviewResponse preview = service.preview(7L, excel(
                row("2024-03", "1250.00", "2024-04-30", "2024-04-15", "1250.00", "TRANSFERENCIA"),
                row("2024-04", "1250.00", "2024-05-31", "2024-05-15", "1250.00", "EFECTIVO")));

        assertEquals(2, preview.resumen().total());
        assertEquals(1, preview.resumen().duplicados());
        assertEquals(1, preview.resumen().importables());
        assertEquals("DUPLICADO", preview.filas().get(0).estado());
        assertEquals("IMPORTABLE", preview.filas().get(1).estado());
        verify(cuotaRepository, never()).saveAndFlush(any(Cuota.class));
    }

    @Test
    void importaCuotaYPagoConSusFechasRealesYActualizaElSaldo() throws Exception {
        when(entityManager.find(Socio.class, 7L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(socio);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(cuotaRepository.existsBySocioIdAndPeriodo(7L, LocalDate.of(2024, 3, 1))).thenReturn(false);
        when(cuotaRepository.saveAndFlush(any(Cuota.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pagoRepository.saveAndFlush(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ImportResponse result = service.importar(7L, excel(
                row("2024-03", "1250.00", "2024-04-30", "2024-04-01", "500.00", "EFECTIVO"),
                row("2024-03", "1250.00", "2024-04-30", "2024-04-15", "750.00", "TRANSFERENCIA")));

        ArgumentCaptor<Cuota> cuotaCaptor = ArgumentCaptor.forClass(Cuota.class);
        ArgumentCaptor<Pago> pagoCaptor = ArgumentCaptor.forClass(Pago.class);
        verify(cuotaRepository).saveAndFlush(cuotaCaptor.capture());
        verify(pagoRepository, org.mockito.Mockito.times(2)).saveAndFlush(pagoCaptor.capture());
        Cuota quota = cuotaCaptor.getValue();
        List<Pago> payments = pagoCaptor.getAllValues();
        assertEquals(LocalDate.of(2024, 3, 1), quota.getPeriodo());
        assertEquals(new BigDecimal("1250.00"), quota.getMonto());
        assertEquals(LocalDate.of(2024, 4, 30), quota.getFechaVencimiento());
        assertSame(quota, payments.get(0).getCuota());
        assertSame(quota, payments.get(1).getCuota());
        assertEquals(new BigDecimal("500.00"), payments.get(0).getMontoCobrado());
        assertEquals(LocalDate.of(2024, 4, 1), payments.get(0).getFechaEmision());
        assertEquals(MetodoPago.EFECTIVO, payments.get(0).getMetodoPago());
        assertEquals(new BigDecimal("750.00"), payments.get(1).getMontoCobrado());
        assertEquals(LocalDate.of(2024, 4, 15), payments.get(1).getFechaEmision());
        assertEquals(MetodoPago.TRANSFERENCIA, payments.get(1).getMetodoPago());
        assertEquals(1, result.resumen().creados());
        assertEquals(2, result.filas().size());
        assertEquals("IMPORTADO", result.filas().get(1).estado());
        verify(cajaService).actualizarMorosidad(socio);
        verify(entityManager).createNativeQuery(anyString());
    }

    @Test
    void bloqueaLaImportacionCuandoLaCuotaNoEstaPagadaCompleta() throws Exception {
        when(socioRepository.findById(7L)).thenReturn(Optional.of(socio));

        PreviewResponse preview = service.preview(7L, excel(
                row("2024-03", "1250.00", "2024-04-30", "2024-04-15", "1000.00", "EFECTIVO")));

        assertEquals(1, preview.resumen().errores());
        assertEquals("ERROR", preview.filas().get(0).estado());
        assertEquals("Los pagos del período deben sumar exactamente el monto de la cuota.",
                preview.filas().get(0).motivo());
    }

    private static String[] row(String... values) {
        return values;
    }

    private static MockMultipartFile excel(String[]... rows) throws Exception {
        byte[] file;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Cuotas pagadas");
            String[] headers = {
                "Periodo", "Monto cuota", "Fecha vencimiento", "Fecha pago", "Monto pagado", "Metodo de pago"
            };
            var header = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
            }
            for (int index = 0; index < rows.length; index++) {
                var data = sheet.createRow(index + 1);
                for (int column = 0; column < rows[index].length; column++) {
                    data.createCell(column).setCellValue(rows[index][column]);
                }
            }
            workbook.write(output);
            file = output.toByteArray();
        }
        return new MockMultipartFile("file", "historial.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file);
    }
}
