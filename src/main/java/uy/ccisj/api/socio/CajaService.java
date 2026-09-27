package uy.ccisj.api.socio;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

@Service
public class CajaService {
    private final SocioRepository socioRepository;
    private final CuotaRepository cuotaRepository;
    private final PagoRepository pagoRepository;

    public CajaService(SocioRepository socioRepository, CuotaRepository cuotaRepository, PagoRepository pagoRepository) {
        this.socioRepository = socioRepository;
        this.cuotaRepository = cuotaRepository;
        this.pagoRepository = pagoRepository;
    }

    @Transactional(readOnly = true)
    public CajaResumenDTO getResumen() {
        List<Socio> socios = socioRepository.findAllByOrderByRazonSocialAsc();
        Map<Long, Cuota> ultimaCuotaPorSocio = cuotaRepository.findAllByOrderByPeriodoDesc().stream()
                .collect(java.util.stream.Collectors.toMap(
                        cuota -> cuota.getSocio().getId(),
                        Function.identity(),
                        (primera, segunda) -> primera));
        List<CuentaCajaDTO> cuentas = socios.stream()
                .map(socio -> toCuenta(socio, ultimaCuotaPorSocio.get(socio.getId())))
                .toList();
        return new CajaResumenDTO(
                socios.size(),
                socios.stream().filter(socio -> socio.getEstadoMorosidad() == EstadoMorosidad.AL_DIA).count(),
                socios.stream().filter(socio -> socio.getEstadoMorosidad() == EstadoMorosidad.DEUDA_2_MESES || socio.getEstadoMorosidad() == EstadoMorosidad.MOROSO).count(),
                socios.stream().filter(socio -> socio.getEstadoMorosidad() == EstadoMorosidad.INACTIVO).count(),
                cuentas);
    }

    @Transactional(readOnly = true)
    public HistorialCajaDTO getHistorial(Long socioId) {
        Socio socio = findSocio(socioId);
        List<MovimientoCajaDTO> movimientos = cuotaRepository.findBySocioIdOrderByPeriodoDesc(socioId).stream()
                .map(this::toMovimiento)
                .toList();
        return new HistorialCajaDTO(
                socio.getId(),
                socio.getRazonSocial(),
                socio.getUsuario().getBps(),
                socio.getRubro() == null ? null : socio.getRubro().getNombreRubro(),
                socio.getEstadoMorosidad(),
                movimientos);
    }

    @Transactional
    public CuentaCajaDTO registrarCobro(RegistrarCobroDTO dto) {
        Cuota cuota = cuotaRepository.findById(dto.cuotaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La cuota no existe"));
        if (cuota.getEstado() == EstadoCuota.PAGADO || cuota.getEstado() == EstadoCuota.ANULADA || cuota.getPago() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuota ya no admite cobros");
        }
        if (cuota.getSocio().getEstadoMorosidad() == EstadoMorosidad.INACTIVO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No es posible cobrar a un socio inactivo");
        }

        Pago pago = new Pago(cuota, cuota.getMonto(), LocalDate.now(), dto.metodoPago());
        pago.setNroCobranzaExterno(trimToNull(dto.nroCobranzaExterno()));
        pago.setObservaciones(trimToNull(dto.observaciones()));
        pago.setModificadoAdmin(true);
        pagoRepository.save(pago);
        cuota.setPago(pago);
        cuota.setEstado(EstadoCuota.PAGADO);
        actualizarMorosidad(cuota.getSocio());
        return toCuenta(cuota.getSocio(), cuota);
    }

    private void actualizarMorosidad(Socio socio) {
        if (socio.getEstadoMorosidad() == EstadoMorosidad.INACTIVO) return;
        List<Cuota> pendientes = cuotaRepository.findBySocioIdOrderByPeriodoDesc(socio.getId()).stream()
                .filter(cuota -> cuota.getEstado() == EstadoCuota.PENDIENTE)
                .toList();
        if (pendientes.isEmpty()) {
            socio.setEstadoMorosidad(EstadoMorosidad.AL_DIA);
            return;
        }
        boolean esMoroso = pendientes.stream()
            .anyMatch(cuota -> cuota.getFechaVencimiento().plusMonths(2).isBefore(LocalDate.now()));
        socio.setEstadoMorosidad(esMoroso ? EstadoMorosidad.MOROSO : EstadoMorosidad.DEUDA_2_MESES);
    }

    private Socio findSocio(Long socioId) {
        return socioRepository.findById(socioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El socio no existe"));
    }

    private CuentaCajaDTO toCuenta(Socio socio, Cuota cuota) {
        return new CuentaCajaDTO(
                socio.getId(),
                socio.getUsuario().getBps(),
                socio.getRazonSocial(),
                socio.getRubro() == null ? null : socio.getRubro().getNombreRubro(),
                socio.getTelefono(),
                socio.getEstadoMorosidad(),
                cuota == null ? null : cuota.getId(),
                cuota == null ? null : cuota.getEstado(),
                cuota == null ? null : cuota.getMonto(),
                cuota == null ? null : cuota.getPeriodo(),
                cuota == null ? null : cuota.getFechaVencimiento());
    }

    private MovimientoCajaDTO toMovimiento(Cuota cuota) {
        Pago pago = cuota.getPago();
        return new MovimientoCajaDTO(
                cuota.getId(), cuota.getPeriodo(), cuota.getFechaVencimiento(), cuota.getEstado(), cuota.getMonto(),
                pago == null ? null : pago.getMontoCobrado(),
                pago == null ? null : pago.getFechaEmision(),
                pago == null ? null : pago.getMetodoPago(),
                pago == null ? null : pago.getNroCobranzaExterno(),
                pago == null ? null : pago.getObservaciones());
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }
}