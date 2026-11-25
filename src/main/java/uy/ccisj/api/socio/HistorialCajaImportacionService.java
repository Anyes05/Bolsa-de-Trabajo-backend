package uy.ccisj.api.socio;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.domain.MetodoPago;
import uy.ccisj.api.socio.importacion.HistorialCajaExcelParser;
import uy.ccisj.api.socio.importacion.HistorialCajaExcelParser.RawRow;
import uy.ccisj.api.socio.importacion.dto.HistorialCajaImportacionDtos.Fila;
import uy.ccisj.api.socio.importacion.dto.HistorialCajaImportacionDtos.ImportResponse;
import uy.ccisj.api.socio.importacion.dto.HistorialCajaImportacionDtos.PreviewResponse;
import uy.ccisj.api.socio.importacion.dto.HistorialCajaImportacionDtos.Resumen;

@Service
public class HistorialCajaImportacionService {
    private static final long MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final ZoneId URUGUAY = ZoneId.of("America/Montevideo");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"));

    private final SocioRepository socioRepository;
    private final CuotaRepository cuotaRepository;
    private final PagoRepository pagoRepository;
    private final CajaService cajaService;
    private final EntityManager entityManager;

    public HistorialCajaImportacionService(
            SocioRepository socioRepository,
            CuotaRepository cuotaRepository,
            PagoRepository pagoRepository,
            CajaService cajaService,
            EntityManager entityManager) {
        this.socioRepository = socioRepository;
        this.cuotaRepository = cuotaRepository;
        this.pagoRepository = pagoRepository;
        this.cajaService = cajaService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PreviewResponse preview(Long socioId, MultipartFile file) {
        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El socio no existe."));
        List<PreparedRow> rows = prepare(file, socioId);
        List<Fila> preview = rows.stream().map(PreparedRow::toDto).toList();
        return new PreviewResponse(socioId, socio.getRazonSocial(), summary(preview, 0), preview);
    }

    @Transactional
    public ImportResponse importar(Long socioId, MultipartFile file) {
        Socio socio = entityManager.find(Socio.class, socioId, LockModeType.PESSIMISTIC_WRITE);
        if (socio == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El socio no existe.");
        }

        List<PreparedRow> rows = prepare(file, socioId);
        List<Fila> errors = rows.stream()
                .filter(row -> row.estado.equals("ERROR"))
                .map(PreparedRow::toDto)
                .toList();
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El Excel contiene filas con errores. Corrígelas y vuelve a previsualizarlo.");
        }

        List<PreparedRow> importable = rows.stream().filter(row -> row.estado.equals("IMPORTABLE")).toList();
        if (!importable.isEmpty()) {
            ContabilidadContext.registrar(entityManager, "Importación temporal de historial de cuotas pagadas desde Excel");
            Map<YearMonth, List<PreparedRow>> groupedRows = new LinkedHashMap<>();
            for (PreparedRow row : importable) {
                groupedRows.computeIfAbsent(row.periodo, unused -> new ArrayList<>()).add(row);
            }
            for (Map.Entry<YearMonth, List<PreparedRow>> entry : groupedRows.entrySet()) {
                PreparedRow first = entry.getValue().get(0);
                Cuota cuota = new Cuota(socio, entry.getKey().atDay(1), first.montoCuota, first.fechaVencimiento);
                cuota = cuotaRepository.saveAndFlush(cuota);
                for (PreparedRow row : entry.getValue()) {
                    Pago pago = new Pago(cuota, row.montoPagado, row.fechaPago, row.metodoPago);
                    pago.setNroCobranzaExterno(row.referencia);
                    pago.setObservaciones(row.observaciones);
                    pagoRepository.saveAndFlush(pago);
                }
            }
            cajaService.actualizarMorosidad(socio);
        }

        List<Fila> resultRows = rows.stream()
                .map(row -> row.estado.equals("IMPORTABLE") ? row.withStatus("IMPORTADO", null).toDto() : row.toDto())
                .toList();
        long createdQuotas = importable.stream().map(row -> row.periodo).distinct().count();
        return new ImportResponse(socioId, socio.getRazonSocial(), summary(resultRows, (int) createdQuotas), resultRows);
    }

    private List<PreparedRow> prepare(MultipartFile file, Long socioId) {
        validateFile(file);
        List<RawRow> rawRows;
        try (InputStream input = file.getInputStream()) {
            rawRows = HistorialCajaExcelParser.parse(input);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo Excel.");
        }

        Map<YearMonth, List<Integer>> groupedRows = new LinkedHashMap<>();
        List<PreparedRow> prepared = new ArrayList<>();
        for (RawRow raw : rawRows) {
            PreparedRow row = prepareRow(raw);
            prepared.add(row);
            if (row.periodo != null) {
                groupedRows.computeIfAbsent(row.periodo, unused -> new ArrayList<>()).add(prepared.size() - 1);
            }
        }

        for (Map.Entry<YearMonth, List<Integer>> entry : groupedRows.entrySet()) {
            List<PreparedRow> periodRows = entry.getValue().stream().map(prepared::get).toList();
            if (periodRows.stream().anyMatch(row -> row.estado.equals("ERROR"))) {
                continue;
            }
            PreparedRow first = periodRows.get(0);
            boolean consistentQuota = periodRows.stream().allMatch(row ->
                    row.montoCuota.compareTo(first.montoCuota) == 0
                            && row.fechaVencimiento.equals(first.fechaVencimiento));
            if (!consistentQuota) {
                replaceRows(prepared, entry.getValue(),
                        "ERROR", "Las filas del mismo período deben repetir el mismo monto y vencimiento de cuota.");
                continue;
            }
            BigDecimal payments = periodRows.stream().map(row -> row.montoPagado)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (payments.compareTo(first.montoCuota) != 0) {
                replaceRows(prepared, entry.getValue(),
                        "ERROR", "Los pagos del período deben sumar exactamente el monto de la cuota.");
                continue;
            }
            if (cuotaRepository.existsBySocioIdAndPeriodo(socioId, entry.getKey().atDay(1))) {
                replaceRows(prepared, entry.getValue(),
                        "DUPLICADO", "El socio ya tiene una cuota para este período.");
            }
        }
        return prepared;
    }

    private static void replaceRows(List<PreparedRow> rows, List<Integer> indexes, String status, String reason) {
        for (int index : indexes) {
            rows.set(index, rows.get(index).withStatus(status, reason));
        }
    }

    private PreparedRow prepareRow(RawRow raw) {
        try {
            YearMonth periodo = YearMonth.parse(raw.periodo().trim());
            YearMonth mesActual = YearMonth.from(LocalDate.now(URUGUAY));
            if (periodo.isAfter(mesActual)) {
                throw new IllegalArgumentException("El período no puede ser futuro.");
            }
            BigDecimal montoCuota = parseAmount(raw.montoCuota(), "Monto cuota");
            BigDecimal montoPagado = parseAmount(raw.montoPagado(), "Monto pagado");
            LocalDate fechaVencimiento = parseDate(raw.fechaVencimiento(), "Fecha vencimiento");
            if (fechaVencimiento.isBefore(periodo.atDay(1))) {
                throw new IllegalArgumentException("La fecha de vencimiento no puede ser anterior al período.");
            }
            LocalDate fechaPago = parseDate(raw.fechaPago());
            if (fechaPago.isAfter(LocalDate.now(URUGUAY))) {
                throw new IllegalArgumentException("La fecha de pago no puede ser futura.");
            }
            MetodoPago metodoPago = parseMethod(raw.metodoPago());
            String referencia = optionalText(raw.referencia(), 80, "Referencia");
            String observaciones = optionalText(raw.observaciones(), 2000, "Observaciones");
            return new PreparedRow(raw.filaExcel(), periodo, montoCuota, fechaVencimiento, fechaPago, montoPagado,
                    metodoPago, referencia, observaciones, "IMPORTABLE", null);
        } catch (DateTimeParseException exception) {
            return error(raw, "Período inválido. Usa AAAA-MM.");
        } catch (IllegalArgumentException exception) {
            return error(raw, exception.getMessage());
        }
    }

    private static BigDecimal parseAmount(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " es obligatorio.");
        }
        String normalized = value.trim().replaceAll("(?i)UYU", "").replace("$", "").replaceAll("\\s", "");
        int comma = normalized.lastIndexOf(',');
        int dot = normalized.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            normalized = comma > dot
                    ? normalized.replace(".", "").replace(',', '.')
                    : normalized.replace(",", "");
        } else if (comma >= 0) {
            normalized = normalized.replace(',', '.');
        }
        try {
            BigDecimal amount = new BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0 || amount.precision() > 12) {
                throw new IllegalArgumentException(label + " debe ser mayor que cero y tener como máximo 10 dígitos enteros.");
            }
            return amount;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(label + " no es un importe válido con hasta dos decimales.");
        }
    }

    private static LocalDate parseDate(String value) {
        return parseDate(value, "Fecha pago");
    }

    private static LocalDate parseDate(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " es obligatoria.");
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported spreadsheet date format.
            }
        }
        throw new IllegalArgumentException(label + " inválida. Usa AAAA-MM-DD o DD/MM/AAAA.");
    }

    private static MetodoPago parseMethod(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Método de pago es obligatorio.");
        }
        String method = value.trim().toUpperCase(Locale.ROOT);
        try {
            return MetodoPago.valueOf(method);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Método inválido. Usa EFECTIVO, TRANSFERENCIA o COBRADOR.");
        }
    }

    private static String optionalText(String value, int maxLength, String label) {
        String text = value == null ? "" : value.trim();
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(label + " supera el máximo de " + maxLength + " caracteres.");
        }
        return text.isEmpty() ? null : text;
    }

    private static PreparedRow error(RawRow raw, String reason) {
        return new PreparedRow(raw.filaExcel(), null, null, null, null, null, null,
                raw.referencia(), raw.observaciones(), "ERROR", reason);
    }

    private static Resumen summary(List<Fila> rows, int created) {
        return new Resumen(
                rows.size(),
                (int) rows.stream().filter(row -> row.estado().equals("IMPORTABLE")).count(),
                (int) rows.stream().filter(row -> row.estado().equals("DUPLICADO")).count(),
                (int) rows.stream().filter(row -> row.estado().equals("ERROR")).count(),
                created);
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccioná un archivo Excel.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || filename.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe ser .xlsx o .xls.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El Excel no puede superar 10 MB.");
        }
    }

    private record PreparedRow(
            int filaExcel,
            YearMonth periodo,
            BigDecimal montoCuota,
            LocalDate fechaVencimiento,
            LocalDate fechaPago,
            BigDecimal montoPagado,
            MetodoPago metodoPago,
            String referencia,
            String observaciones,
            String estado,
            String motivo) {
        PreparedRow withStatus(String nextStatus, String reason) {
            return new PreparedRow(filaExcel, periodo, montoCuota, fechaVencimiento, fechaPago, montoPagado,
                    metodoPago, referencia, observaciones, nextStatus, reason);
        }

        Fila toDto() {
            return new Fila(filaExcel, periodo == null ? null : periodo.toString(), montoCuota,
                    fechaVencimiento == null ? null : fechaVencimiento.toString(),
                    fechaPago == null ? null : fechaPago.toString(), montoPagado,
                    metodoPago == null ? null : metodoPago.name(), referencia, observaciones, estado, motivo);
        }
    }
}
