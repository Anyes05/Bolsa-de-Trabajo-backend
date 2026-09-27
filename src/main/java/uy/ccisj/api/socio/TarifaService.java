package uy.ccisj.api.socio;

import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.ccisj.api.domain.EstadoCuota;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.socio.dto.FacturacionGeneradaDTO;
import uy.ccisj.api.socio.dto.GenerarFacturacionDTO;
import uy.ccisj.api.socio.dto.GuardarTarifaDTO;
import uy.ccisj.api.socio.dto.TarifaDTO;

@Service
public class TarifaService {
    private final TarifaRepository tarifaRepository;
    private final CuotaRepository cuotaRepository;
    private final SocioRepository socioRepository;

    public TarifaService(TarifaRepository tarifaRepository, CuotaRepository cuotaRepository, SocioRepository socioRepository) {
        this.tarifaRepository = tarifaRepository;
        this.cuotaRepository = cuotaRepository;
        this.socioRepository = socioRepository;
    }

    @Transactional(readOnly = true)
    public List<TarifaDTO> listar() {
        return tarifaRepository.findAllByOrderByPeriodoDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public TarifaDTO guardar(GuardarTarifaDTO dto) {
        LocalDate periodo = primerDiaDelMes(dto.periodo());
        Tarifa tarifa = tarifaRepository.findByPeriodo(periodo).orElse(null);
        if (tarifa != null && cuotaRepository.existsByTarifaId(tarifa.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La tarifa no se puede modificar porque ya tiene cuotas emitidas");
        }
        if (tarifa == null) {
            tarifa = new Tarifa(periodo, dto.montoBase(), dto.fechaVencimiento());
        } else {
            tarifa.setMontoBase(dto.montoBase());
            tarifa.setFechaVencimiento(dto.fechaVencimiento());
        }
        return toDto(tarifaRepository.save(tarifa));
    }

    @Transactional
    public FacturacionGeneradaDTO generarFacturacion(GenerarFacturacionDTO dto) {
        LocalDate periodo = primerDiaDelMes(dto.periodo());
        Tarifa tarifa = tarifaRepository.findByPeriodo(periodo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Primero debe definir la tarifa para el período seleccionado"));
        List<Socio> sociosActivos = socioRepository.findAllByOrderByRazonSocialAsc().stream()
                .filter(socio -> socio.getEstadoMorosidad() != EstadoMorosidad.INACTIVO)
                .toList();
        List<Cuota> cuotasNuevas = sociosActivos.stream()
                .filter(socio -> !cuotaRepository.existsBySocioIdAndPeriodo(socio.getId(), periodo))
                .map(socio -> crearCuota(socio, tarifa))
                .toList();

        cuotaRepository.saveAll(cuotasNuevas);
        cuotaRepository.flush();
        sociosActivos.forEach(this::actualizarMorosidad);

        return new FacturacionGeneradaDTO(periodo, tarifa.getId(), sociosActivos.size(), cuotasNuevas.size(),
                sociosActivos.size() - cuotasNuevas.size());
    }

    private Cuota crearCuota(Socio socio, Tarifa tarifa) {
        Cuota cuota = new Cuota(socio, tarifa.getPeriodo(), tarifa.getMontoBase(), tarifa.getFechaVencimiento());
        cuota.setTarifa(tarifa);
        return cuota;
    }

    private void actualizarMorosidad(Socio socio) {
        List<Cuota> cuotas = cuotaRepository.findBySocioIdOrderByPeriodoDesc(socio.getId());
        boolean esMoroso = cuotas.stream()
            .anyMatch(cuota -> cuota.getEstado() == EstadoCuota.PENDIENTE
                && cuota.getFechaVencimiento().plusMonths(2).isBefore(LocalDate.now()));
        boolean hayCuotaPendiente = cuotas.stream()
            .anyMatch(cuota -> cuota.getEstado() == EstadoCuota.PENDIENTE);
        socio.setEstadoMorosidad(esMoroso ? EstadoMorosidad.MOROSO
            : hayCuotaPendiente ? EstadoMorosidad.DEUDA_2_MESES : EstadoMorosidad.AL_DIA);
    }

    private TarifaDTO toDto(Tarifa tarifa) {
        return new TarifaDTO(tarifa.getId(), tarifa.getPeriodo(), tarifa.getMontoBase(), tarifa.getFechaVencimiento(), tarifa.getCreatedAt());
    }

    private LocalDate primerDiaDelMes(LocalDate fecha) {
        return fecha.withDayOfMonth(1);
    }
}