package uy.ccisj.api.socio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.domain.EstadoCuota;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.socio.dto.CajaResumenDTO;
import uy.ccisj.api.socio.dto.CuentaCajaDTO;
import uy.ccisj.api.socio.dto.HistorialCajaDTO;
import uy.ccisj.api.socio.dto.MovimientoCajaDTO;
import uy.ccisj.api.socio.dto.RegistrarCobroDTO;
import uy.ccisj.api.socio.dto.RegistrarReintegroDTO;
import uy.ccisj.api.socio.dto.ReintegroProgramadoDTO;

@Service
public class CajaService {
    private final SocioRepository socioRepository;
    private final CuotaRepository cuotaRepository;
    private final PagoRepository pagoRepository;
    private final ReintegroRepository reintegroRepository;
    private final ReintegroProgramadoRepository reintegroProgramadoRepository;
    private final EntityManager entityManager;

    public CajaService(
            SocioRepository socioRepository,
            CuotaRepository cuotaRepository,
            PagoRepository pagoRepository,
            ReintegroRepository reintegroRepository,
            ReintegroProgramadoRepository reintegroProgramadoRepository,
            EntityManager entityManager) {
        this.socioRepository = socioRepository;
        this.cuotaRepository = cuotaRepository;
        this.pagoRepository = pagoRepository;
        this.reintegroRepository = reintegroRepository;
        this.reintegroProgramadoRepository = reintegroProgramadoRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public CajaResumenDTO getResumen() {
        List<CuentaCajaDTO> cuentas = socioRepository.findAllByOrderByRazonSocialAsc().stream()
                .map(this::toCuenta)
                .toList();
        return new CajaResumenDTO(
                cuentas.size(),
                cuentas.stream().filter(cuenta -> cuenta.estadoMorosidad() == EstadoMorosidad.AL_DIA).count(),
                cuentas.stream().filter(cuenta -> cuenta.estadoMorosidad() == EstadoMorosidad.MOROSO).count(),
                cuentas.stream().filter(cuenta -> cuenta.estadoMorosidad() == EstadoMorosidad.INACTIVO).count(),
                cuentas);
    }

    @Transactional(readOnly = true)
    public HistorialCajaDTO getHistorial(Long socioId) {
        Socio socio = findSocio(socioId, false);
        List<MovimientoCajaDTO> registros = listarHistorial(socioId);
        SaldoCuenta.Resultado resultado = calcularSaldo(registros);
        BigDecimal saldo = BigDecimal.ZERO;
        List<MovimientoCajaDTO> historial = new ArrayList<>();
        for (MovimientoCajaDTO registro : registros) {
            BigDecimal saldoAnterior = saldo;
            saldo = saldo.add(esPago(registro) ? registro.monto().negate() : registro.monto());
            historial.add(new MovimientoCajaDTO(
                    registro.id(),
                    registro.tipo(),
                    registro.concepto(),
                    registro.fecha(),
                    registro.fechaVencimiento(),
                    registro.monto(),
                    saldoAnterior,
                    saldo,
                    resultado.pendientes().getOrDefault(claveCargo(registro), BigDecimal.ZERO),
                    registro.metodoPago(),
                    registro.referencia(),
                    registro.observaciones()));
        }
        List<ReintegroProgramadoDTO> reintegrosProgramados = reintegroProgramadoRepository
                .findBySocioIdOrderByIdAsc(socioId).stream()
                .map(this::toProgramado)
                .toList();
        return new HistorialCajaDTO(
                socioId,
                socio.getRazonSocial(),
                socio.getUsuario().getBps(),
                socio.getRubro() == null ? null : socio.getRubro().getNombreRubro(),
                calcularEstadoMorosidad(socio, resultado),
                resultado.saldo(),
                resultado.deudaVencida(),
                historial,
                reintegrosProgramados);
    }

    @Transactional
    public CuentaCajaDTO registrarCobro(RegistrarCobroDTO dto) {
        Socio socio = findSocio(dto.socioId(), true);
        validarActivo(socio);
        Pago pago = new Pago(socio, dto.montoRecibido(), LocalDate.now(), dto.metodoPago());
        pago.setNroCobranzaExterno(trimToNull(dto.nroCobranzaExterno()));
        pago.setObservaciones(trimToNull(dto.observaciones()));
        pagoRepository.saveAndFlush(pago);
        actualizarMorosidad(socio);
        return toCuenta(socio);
    }

    @Transactional
    public CuentaCajaDTO registrarReintegro(Long socioId, RegistrarReintegroDTO dto) {
        Socio socio = findSocio(socioId, true);
        validarActivo(socio);
        LocalDate fecha = LocalDate.now();
        String origen = null;
        if (dto.recurrente()) {
            ReintegroProgramado recurrente = reintegroProgramadoRepository.saveAndFlush(new ReintegroProgramado(socio,
                    dto.concepto().trim(), dto.monto(), fecha.withDayOfMonth(1)));
            origen = origenProgramado(recurrente, fecha.withDayOfMonth(1));
        }
        reintegroRepository.saveAndFlush(new Reintegro(socio, dto.concepto().trim(),
                dto.monto(), fecha, SaldoCuenta.vencimiento(fecha), origen));
        actualizarMorosidad(socio);
        return toCuenta(socio);
    }

    @Transactional
    public void eliminarProgramado(Long socioId, Long reintegroId) {
        Socio socio = findSocio(socioId, true);
        ReintegroProgramado programado = findProgramado(socioId, reintegroId);
        conservarReintegroDelMes(socio, programado);
        reintegroProgramadoRepository.delete(programado);
    }

    private ReintegroProgramado findProgramado(Long socioId, Long reintegroId) {
        return reintegroProgramadoRepository.findById(reintegroId)
                .filter(registro -> registro.getSocio().getId().equals(socioId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El reintegro no existe"));
    }

    @Transactional
    public ReintegroProgramadoDTO actualizarProgramado(Long socioId, Long reintegroId, ReintegroProgramadoDTO dto) {
        Socio socio = findSocio(socioId, true);
        ReintegroProgramado programado = findProgramado(socioId, reintegroId);
        if (dto.activo()) {
            validarActivo(socio);
        }
        conservarReintegroDelMes(socio, programado);
        if (!programado.isActivo() && dto.activo()) {
            programado.setDesdePeriodo(LocalDate.now().withDayOfMonth(1).plusMonths(1));
        }
        programado.setConcepto(dto.concepto().trim());
        programado.setMonto(dto.monto());
        programado.setActivo(dto.activo());
        return toProgramado(programado);
    }

    @Transactional
    public void generarReintegros(Socio socio, LocalDate periodo) {
        findSocio(socio.getId(), true);
        for (ReintegroProgramado programado : reintegroProgramadoRepository.findBySocioIdOrderByIdAsc(socio.getId())) {
            generarReintegro(programado, periodo);
        }
        reintegroRepository.flush();
    }

    private void conservarReintegroDelMes(Socio socio, ReintegroProgramado programado) {
        if (socio.getEstadoMorosidad() == EstadoMorosidad.INACTIVO) {
            return;
        }
        generarReintegro(programado, LocalDate.now().withDayOfMonth(1));
        reintegroRepository.flush();
        actualizarMorosidad(socio);
    }

    private void generarReintegro(ReintegroProgramado programado, LocalDate periodo) {
        String origen = origenProgramado(programado, periodo);
        if (programado.isActivo() && !periodo.isBefore(programado.getDesdePeriodo())
                && !reintegroRepository.existsByOrigen(origen)) {
            reintegroRepository.save(new Reintegro(programado.getSocio(), programado.getConcepto(),
                    programado.getMonto(), periodo, SaldoCuenta.vencimiento(periodo), origen));
        }
    }

    @Transactional
    public void actualizarMorosidad(Socio socio) {
        SaldoCuenta.Resultado resultado = calcularSaldo(listarHistorial(socio.getId()));
        for (Cuota cuota : cuotaRepository.findBySocioIdOrderByPeriodoDesc(socio.getId())) {
            if (cuota.getEstado() != EstadoCuota.ANULADA) {
                cuota.setEstado(resultado.pendientes().get("CUOTA:" + cuota.getId()).signum() == 0
                        ? EstadoCuota.PAGADO : EstadoCuota.PENDIENTE);
            }
        }
        socio.setEstadoMorosidad(calcularEstadoMorosidad(socio, resultado));
    }

    private List<MovimientoCajaDTO> listarHistorial(Long socioId) {
        List<MovimientoCajaDTO> historial = new ArrayList<>();
        for (Cuota cuota : cuotaRepository.findBySocioIdOrderByPeriodoDesc(socioId)) {
            if (cuota.getEstado() != EstadoCuota.ANULADA) {
                historial.add(new MovimientoCajaDTO(cuota.getId(), "CUOTA",
                        "Cuota " + YearMonth.from(cuota.getPeriodo()), cuota.getPeriodo(),
                        cuota.getFechaVencimiento(), cuota.getMonto(), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, null, null, null));
            }
        }
        for (Reintegro reintegro : reintegroRepository.findBySocioIdOrderByFechaAscIdAsc(socioId)) {
            if (reintegro.getCuota() == null || reintegro.getCuota().getEstado() != EstadoCuota.ANULADA) {
                historial.add(new MovimientoCajaDTO(reintegro.getId(), "REINTEGRO", reintegro.getConcepto(),
                        reintegro.getFecha(), reintegro.getFechaVencimiento(), reintegro.getMonto(),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null));
            }
        }
        for (Pago pago : pagoRepository.findBySocioIdOrderByFechaEmisionAscIdAsc(socioId)) {
            if (pago.getCuota() == null || pago.getCuota().getEstado() != EstadoCuota.ANULADA) {
                historial.add(new MovimientoCajaDTO(pago.getId(), "PAGO", "Pago recibido", pago.getFechaEmision(),
                        null, pago.getMontoCobrado(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        pago.getMetodoPago(), pago.getNroCobranzaExterno(), pago.getObservaciones()));
            }
        }
        historial.sort(Comparator.comparing(MovimientoCajaDTO::fecha)
                .thenComparingInt(registro -> switch (registro.tipo()) {
                    case "CUOTA" -> 0;
                    case "REINTEGRO" -> 1;
                    default -> 2;
                })
                .thenComparing(MovimientoCajaDTO::id));
        return historial;
    }

    private SaldoCuenta.Resultado calcularSaldo(List<MovimientoCajaDTO> registros) {
        List<SaldoCuenta.Cargo> cargos = registros.stream()
                .filter(registro -> !esPago(registro))
                .map(registro -> new SaldoCuenta.Cargo(claveCargo(registro), registro.fecha(),
                        registro.fechaVencimiento(), registro.monto()))
                .toList();
        BigDecimal totalPagos = registros.stream()
                .filter(this::esPago)
                .map(MovimientoCajaDTO::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return SaldoCuenta.calcular(cargos, totalPagos, LocalDate.now());
    }

    private boolean esPago(MovimientoCajaDTO registro) {
        return "PAGO".equals(registro.tipo());
    }

    private String claveCargo(MovimientoCajaDTO registro) {
        return registro.tipo() + ":" + registro.id();
    }

    private CuentaCajaDTO toCuenta(Socio socio) {
        SaldoCuenta.Resultado resultado = calcularSaldo(listarHistorial(socio.getId()));
        Cuota cuota = cuotaRepository.findFirstBySocioIdOrderByPeriodoDesc(socio.getId()).orElse(null);
        return new CuentaCajaDTO(
                socio.getId(),
                socio.getUsuario().getBps(),
                socio.getRazonSocial(),
                socio.getRubro() == null ? null : socio.getRubro().getNombreRubro(),
                socio.getTelefono(),
                calcularEstadoMorosidad(socio, resultado),
                cuota == null ? null : cuota.getId(),
                cuota == null ? null : cuota.getEstado(),
                cuota == null ? null : cuota.getMonto(),
                BigDecimal.ZERO,
                false,
                cuota == null ? null : cuota.getPeriodo(),
                cuota == null ? null : cuota.getFechaVencimiento(),
                resultado.saldo(),
                resultado.deudaVencida());
    }

    private EstadoMorosidad calcularEstadoMorosidad(Socio socio, SaldoCuenta.Resultado resultado) {
        if (socio.getEstadoMorosidad() == EstadoMorosidad.INACTIVO
                || (socio.getUsuario() != null && !socio.getUsuario().isActivo())) {
            return EstadoMorosidad.INACTIVO;
        }
        return resultado.deudaVencida().signum() > 0 ? EstadoMorosidad.MOROSO : EstadoMorosidad.AL_DIA;
    }

    private Socio findSocio(Long socioId, boolean bloquear) {
        Socio socio = bloquear ? entityManager.find(Socio.class, socioId, LockModeType.PESSIMISTIC_WRITE)
                : socioRepository.findById(socioId).orElse(null);
        if (socio == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El socio no existe");
        }
        return socio;
    }

    private void validarActivo(Socio socio) {
        if (socio.getEstadoMorosidad() == EstadoMorosidad.INACTIVO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El socio esta inactivo");
        }
    }

    private ReintegroProgramadoDTO toProgramado(ReintegroProgramado registro) {
        return new ReintegroProgramadoDTO(registro.getId(), registro.getConcepto(), registro.getMonto(), registro.isActivo());
    }

    private String origenProgramado(ReintegroProgramado registro, LocalDate periodo) {
        return "recurrente:" + registro.getId() + ":" + periodo.withDayOfMonth(1);
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}