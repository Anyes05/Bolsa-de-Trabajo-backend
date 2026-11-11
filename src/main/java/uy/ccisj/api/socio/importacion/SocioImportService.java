package uy.ccisj.api.socio.importacion;

import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.auth.PasswordPolicy;
import uy.ccisj.api.catalogo.LocalidadesSanJose;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.catalogo.RubroEmpleoRepository;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.socio.Direccion;
import uy.ccisj.api.socio.DireccionRepository;
import uy.ccisj.api.socio.Socio;
import uy.ccisj.api.socio.SocioRepository;
import uy.ccisj.api.socio.SocioSubscriptionHistory;
import uy.ccisj.api.socio.importacion.dto.ImportacionDtos.ImportResumen;
import uy.ccisj.api.socio.importacion.dto.ImportacionDtos.ImportResponse;
import uy.ccisj.api.socio.importacion.dto.ImportacionDtos.PreviewFila;
import uy.ccisj.api.socio.importacion.dto.ImportacionDtos.PreviewResponse;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

@Service
public class SocioImportService {
    public static final String RUBRO_SIN_CLASIFICAR = "Sin clasificar";
    public static final String CALLE_PENDIENTE = "Dato pendiente de carga";
    public static final String TELEFONO_PENDIENTE = "00000000";
    public static final LocalDate FECHA_PENDIENTE = LocalDate.of(2000, 1, 1);
    private static final Pattern NUMERO_FINAL = Pattern.compile("^(.*?)\\s+(\\d+[A-Za-z0-9\\-/]*)$");

    private final SocioRepository socioRepository;
    private final UserRepository userRepository;
    private final DireccionRepository direccionRepository;
    private final RubroEmpleoRepository rubroEmpleoRepository;
    private final PasswordEncoder passwordEncoder;
    private final SocioSubscriptionHistory subscriptionHistory;

    public SocioImportService(
            SocioRepository socioRepository,
            UserRepository userRepository,
            DireccionRepository direccionRepository,
            RubroEmpleoRepository rubroEmpleoRepository,
            PasswordEncoder passwordEncoder,
            SocioSubscriptionHistory subscriptionHistory) {
        this.socioRepository = socioRepository;
        this.userRepository = userRepository;
        this.direccionRepository = direccionRepository;
        this.rubroEmpleoRepository = rubroEmpleoRepository;
        this.passwordEncoder = passwordEncoder;
        this.subscriptionHistory = subscriptionHistory;
    }

    @Transactional(readOnly = true)
    public PreviewResponse preview(MultipartFile file) {
        List<PreparedRow> prepared = prepare(file);
        List<PreviewFila> filas = prepared.stream().map(PreparedRow::toPreview).toList();
        return new PreviewResponse(resumen(filas, 0), filas);
    }

    @Transactional
    public ImportResponse importar(MultipartFile file) {
        List<PreparedRow> prepared = prepare(file);
        int creados = 0;
        List<PreviewFila> omitidas = new ArrayList<>();
        RubroEmpleo sinClasificar = requireRubroSinClasificar();
        FictionalCounters counters = new FictionalCounters();
        for (PreparedRow row : prepared) {
            if (!esImportable(row.estado)) {
                omitidas.add(row.toPreview());
                continue;
            }
            try {
                persist(row, sinClasificar);
                creados++;
            } catch (RuntimeException exception) {
                omitidas.add(row.withError("No se pudo guardar: " + exception.getMessage()).toPreview());
            }
        }
        List<PreviewFila> todas = prepared.stream().map(PreparedRow::toPreview).toList();
        return new ImportResponse(resumen(todas, creados), omitidas);
    }

    private List<PreparedRow> prepare(MultipartFile file) {
        validateFile(file);
        List<PadronExcelParser.RawRow> rawRows;
        try (InputStream input = file.getInputStream()) {
            rawRows = PadronExcelParser.parse(input);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo Excel");
        }
        if (rawRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El Excel no tiene filas de socios para importar");
        }

        Set<String> usedBps = new HashSet<>();
        Set<String> usedRut = new HashSet<>();
        Set<String> usedEmail = new HashSet<>();
        FictionalCounters counters = new FictionalCounters();
        List<RubroEmpleo> rubros = rubroEmpleoRepository.findAll();
        List<PreparedRow> prepared = new ArrayList<>();
        for (PadronExcelParser.RawRow raw : rawRows) {
            prepared.add(mapRow(raw, usedBps, usedRut, usedEmail, counters, rubros));
        }
        return prepared;
    }

    private PreparedRow mapRow(
            PadronExcelParser.RawRow raw,
            Set<String> usedBps,
            Set<String> usedRut,
            Set<String> usedEmail,
            FictionalCounters counters,
            List<RubroEmpleo> rubros) {
        String razonSocial = blankToNull(raw.razonSocial());
        if (razonSocial == null) {
            return error(raw, "Falta la razón social");
        }

        Set<String> ficticios = new LinkedHashSet<>();
        String bpsDigits = digits(raw.bps());
        boolean bpsFicticio = bpsDigits.length() < 9;
        if (bpsFicticio) {
            do {
                bpsDigits = nextBps(counters);
            } while (userRepository.existsByBps(bpsDigits)
                    || userRepository.existsByCedula(bpsDigits)
                    || usedBps.contains(bpsDigits));
            usedBps.add(bpsDigits);
            ficticios.add(CamposFicticios.BPS);
        } else if (userRepository.existsByBps(bpsDigits)
                || userRepository.existsByCedula(bpsDigits)
                || !usedBps.add(bpsDigits)) {
            return duplicate(raw, razonSocial, "Ya existe un socio con ese BPS (o está repetido en el Excel)");
        }

        String rutDigits = digits(raw.rut());
        boolean rutFicticio = rutDigits.length() != 12 && rutDigits.length() != 8;
        if (rutFicticio) {
            do {
                rutDigits = nextRut(counters);
            } while (socioRepository.existsByRutIgnoreCase(rutDigits) || usedRut.contains(rutDigits));
            usedRut.add(rutDigits);
            ficticios.add(CamposFicticios.RUT);
        } else if (socioRepository.existsByRutIgnoreCase(rutDigits) || !usedRut.add(rutDigits)) {
            return duplicate(raw, razonSocial, "Ya existe un socio con ese RUT (o está repetido en el Excel)");
        }

        String telefono = blankToNull(raw.celular());
        if (telefono == null) {
            telefono = blankToNull(raw.telefono());
        }
        if (telefono == null) {
            telefono = TELEFONO_PENDIENTE;
            ficticios.add(CamposFicticios.TELEFONO);
        }

        String email = normalizeEmail(raw.mail());
        if (email == null) {
            do {
                email = nextEmail(counters);
            } while (userRepository.existsByEmailIgnoreCase(email) || usedEmail.contains(email));
            usedEmail.add(email);
            ficticios.add(CamposFicticios.EMAIL_CONTACTO);
            ficticios.add(CamposFicticios.EMAIL_USUARIO);
        } else if (userRepository.existsByEmailIgnoreCase(email) || !usedEmail.add(email.toLowerCase(Locale.ROOT))) {
            return duplicate(raw, razonSocial, "Ya existe una cuenta con ese correo (o está repetido en el Excel)");
        }

        AddressParts address = splitAddress(raw.direccion());
        if (address.calleFicticia) {
            ficticios.add(CamposFicticios.CALLE);
        }
        String localidad = matchLocalidad(raw.localidad());
        boolean localidadFicticia = localidad.equals("Otra localidad")
                && (blankToNull(raw.localidad()) == null || !localidadEquals(raw.localidad(), "Otra localidad"));
        if (localidadFicticia) {
            ficticios.add(CamposFicticios.LOCALIDAD);
        }

        RubroMatch rubro = matchRubro(raw.giro(), rubros);
        if (rubro.ficticio) {
            ficticios.add(CamposFicticios.RUBRO);
        }

        LocalDate fechaAlta = PadronExcelParser.parseDate(raw.fechaAfiliacion());
        if (fechaAlta == null) {
            fechaAlta = LocalDate.now();
            ficticios.add(CamposFicticios.FECHA_ALTA);
        }
        LocalDate fechaAniversario = PadronExcelParser.parseDate(raw.inicio());
        if (fechaAniversario == null) {
            fechaAniversario = FECHA_PENDIENTE;
            ficticios.add(CamposFicticios.FECHA_ANIVERSARIO);
        }

        String estado = raw.inactivo() ? "INACTIVO" : "IMPORTABLE";
        String motivo = motivoImportacion(raw.inactivo(), !ficticios.isEmpty());
        return new PreparedRow(
                raw.filaExcel(),
                estado,
                motivo,
                razonSocial,
                bpsDigits,
                rutDigits,
                telefono,
                email,
                address.calle,
                address.numero,
                localidad,
                rubro.nombre,
                rubro.id,
                fechaAlta,
                fechaAniversario,
                List.copyOf(ficticios),
                raw.inactivo());
    }

    private void persist(PreparedRow row, RubroEmpleo sinClasificar) {
        String password = PasswordPolicy.generateProvisional(row.razonSocial);
        User user = new User(row.email, row.bps, passwordEncoder.encode(password), Role.SOCIO);
        user.setCedula(null);
        user.setActivo(!row.inactivo);
        if (row.inactivo) {
            user.setFechaBaja(OffsetDateTime.now());
        }
        userRepository.save(user);

        Direccion direccion = direccionRepository.save(new Direccion(row.calle, row.numero, row.localidad));
        Socio socio = new Socio(user, row.razonSocial);
        socio.setRut(row.rut);
        socio.setTelefono(row.telefono);
        socio.setEmailContacto(row.email);
        socio.setDireccion(direccion);
        RubroEmpleo rubro = row.rubroId != null
                ? rubroEmpleoRepository.findById(row.rubroId).orElse(sinClasificar)
                : sinClasificar;
        socio.setRubro(rubro);
        socio.setEsDirectivo(false);
        socio.setFechaAlta(row.fechaAlta);
        socio.setFechaAniversario(row.fechaAniversario);
        socio.setEstadoMorosidad(row.inactivo ? EstadoMorosidad.INACTIVO : EstadoMorosidad.AL_DIA);
        socio.setImportadoMasivo(true);
        socio.setAccesoHabilitado(false);
        socio.setCamposFicticios(CamposFicticios.serialize(new LinkedHashSet<>(row.camposFicticios)));
        Socio saved = socioRepository.save(socio);
        subscriptionHistory.record(saved.getId(), "ALTA", saved.getFechaAlta());
        if (row.inactivo) {
            LocalDate baja = subscriptionHistory.today();
            if (saved.getFechaAlta() != null && baja.isBefore(saved.getFechaAlta())) {
                baja = saved.getFechaAlta();
            }
            subscriptionHistory.record(saved.getId(), "BAJA", baja);
        }
    }

    private RubroEmpleo requireRubroSinClasificar() {
        return rubroEmpleoRepository.findByNombreRubroIgnoreCase(RUBRO_SIN_CLASIFICAR)
                .orElseGet(() -> rubroEmpleoRepository.save(new RubroEmpleo(RUBRO_SIN_CLASIFICAR)));
    }

    private static RubroMatch matchRubro(String giro, List<RubroEmpleo> rubros) {
        String value = blankToNull(giro);
        if (value != null) {
            String needle = fold(value);
            for (RubroEmpleo rubro : rubros) {
                if (fold(rubro.getNombreRubro()).equals(needle)) {
                    return new RubroMatch(rubro.getId(), rubro.getNombreRubro(), false);
                }
            }
        }
        for (RubroEmpleo rubro : rubros) {
            if (fold(rubro.getNombreRubro()).equals(fold(RUBRO_SIN_CLASIFICAR))) {
                return new RubroMatch(rubro.getId(), rubro.getNombreRubro(), true);
            }
        }
        return new RubroMatch(null, RUBRO_SIN_CLASIFICAR, true);
    }

    private static String matchLocalidad(String raw) {
        String value = blankToNull(raw);
        if (value != null) {
            for (String option : LocalidadesSanJose.LOCALIDADES) {
                if (fold(option).equals(fold(value))) {
                    return option;
                }
            }
        }
        return "Otra localidad";
    }

    private static boolean localidadEquals(String raw, String expected) {
        return raw != null && fold(raw).equals(fold(expected));
    }

    private static AddressParts splitAddress(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return new AddressParts(CALLE_PENDIENTE, null, true);
        }
        Matcher matcher = NUMERO_FINAL.matcher(value);
        if (matcher.matches()) {
            String calle = matcher.group(1).trim();
            String numero = matcher.group(2).trim();
            if (!calle.isEmpty() && numero.length() <= 20) {
                return new AddressParts(truncate(calle, 160), numero, false);
            }
        }
        return new AddressParts(truncate(value, 160), null, false);
    }

    private static ImportResumen resumen(List<PreviewFila> filas, int creados) {
        int importables = 0;
        int conFicticios = 0;
        int duplicados = 0;
        int errores = 0;
        int inactivos = 0;
        for (PreviewFila fila : filas) {
            switch (fila.estado()) {
                case "IMPORTABLE", "INACTIVO" -> {
                    importables++;
                    if ("INACTIVO".equals(fila.estado())) {
                        inactivos++;
                    }
                    if (fila.camposFicticios() != null && !fila.camposFicticios().isEmpty()) {
                        conFicticios++;
                    }
                }
                case "DUPLICADO" -> duplicados++;
                default -> errores++;
            }
        }
        return new ImportResumen(filas.size(), importables, conFicticios, duplicados, errores, inactivos, creados);
    }

    private static boolean esImportable(String estado) {
        return "IMPORTABLE".equals(estado) || "INACTIVO".equals(estado);
    }

    private static String motivoImportacion(boolean inactivo, boolean conFicticios) {
        if (inactivo && conFicticios) {
            return "Celda en rojo en el Excel: se importará como socio inactivo. Se completarán datos provisionales en los campos marcados";
        }
        if (inactivo) {
            return "Celda en rojo en el Excel: se importará como socio inactivo";
        }
        if (conFicticios) {
            return "Se completarán datos provisionales en los campos marcados";
        }
        return null;
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccioná un archivo Excel");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe ser .xlsx o .xls");
        }
    }

    private static PreparedRow error(PadronExcelParser.RawRow raw, String motivo) {
        return new PreparedRow(raw.filaExcel(), "ERROR", motivo, blankToNull(raw.razonSocial()),
                null, null, null, null, null, null, null, null, null, null, null, List.of(), raw.inactivo());
    }

    private static PreparedRow duplicate(PadronExcelParser.RawRow raw, String razon, String motivo) {
        return new PreparedRow(raw.filaExcel(), "DUPLICADO", motivo, razon,
                digits(raw.bps()), digits(raw.rut()), null, normalizeEmail(raw.mail()),
                null, null, null, null, null, null, null, List.of(), raw.inactivo());
    }

    private static String nextBps(FictionalCounters counters) {
        return String.format(Locale.ROOT, "99%010d", counters.next());
    }

    private static String nextRut(FictionalCounters counters) {
        return String.format(Locale.ROOT, "98%010d", counters.next());
    }

    private static String nextEmail(FictionalCounters counters) {
        return "import." + counters.next() + "@incompleto.ccisj.local";
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static String normalizeEmail(String value) {
        String email = blankToNull(value);
        if (email == null || !email.contains("@") || email.contains(" ")) {
            return null;
        }
        return email.toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record AddressParts(String calle, String numero, boolean calleFicticia) {}

    private record RubroMatch(Long id, String nombre, boolean ficticio) {}

    private static final class FictionalCounters {
        private long value = 1;

        long next() {
            return value++;
        }
    }

    private record PreparedRow(
            int filaExcel,
            String estado,
            String motivo,
            String razonSocial,
            String bps,
            String rut,
            String telefono,
            String email,
            String calle,
            String numero,
            String localidad,
            String rubro,
            Long rubroId,
            LocalDate fechaAlta,
            LocalDate fechaAniversario,
            List<String> camposFicticios,
            boolean inactivo) {

        PreviewFila toPreview() {
            return new PreviewFila(
                    filaExcel, estado, motivo, razonSocial, bps, rut, telefono, email,
                    calle, numero, localidad, rubro,
                    fechaAlta == null ? null : fechaAlta.toString(),
                    fechaAniversario == null ? null : fechaAniversario.toString(),
                    camposFicticios,
                    inactivo);
        }

        PreparedRow withError(String nuevoMotivo) {
            return new PreparedRow(filaExcel, "ERROR", nuevoMotivo, razonSocial, bps, rut, telefono, email,
                    calle, numero, localidad, rubro, rubroId, fechaAlta, fechaAniversario, camposFicticios, inactivo);
        }
    }
}
