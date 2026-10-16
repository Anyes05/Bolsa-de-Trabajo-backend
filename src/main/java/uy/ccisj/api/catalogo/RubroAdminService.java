package uy.ccisj.api.catalogo;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RubroAdminService {
    private final RubroEmpleoRepository rubroEmpleoRepository;

    public RubroAdminService(RubroEmpleoRepository rubroEmpleoRepository) {
        this.rubroEmpleoRepository = rubroEmpleoRepository;
    }

    @Transactional(readOnly = true)
    public List<RubroAdminDTO> list(boolean incluirInactivos) {
        List<RubroEmpleo> rubros = incluirInactivos
                ? rubroEmpleoRepository.findAllByOrderByNombreRubroAsc()
                : rubroEmpleoRepository.findByActivoTrueOrderByNombreRubroAsc();
        return rubros.stream().map(this::toDto).toList();
    }

    @Transactional
    public RubroAdminDTO create(String nombreRubro) {
        String nombre = normalize(nombreRubro);
        if (rubroEmpleoRepository.existsByNombreRubroIgnoreCase(nombre)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un rubro con ese nombre");
        }
        return toDto(rubroEmpleoRepository.save(new RubroEmpleo(nombre)));
    }

    @Transactional
    public RubroAdminDTO update(Long id, String nombreRubro) {
        RubroEmpleo rubro = find(id);
        String nombre = normalize(nombreRubro);
        if (rubroEmpleoRepository.existsByNombreRubroIgnoreCaseAndIdNot(nombre, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un rubro con ese nombre");
        }
        rubro.setNombreRubro(nombre);
        return toDto(rubroEmpleoRepository.save(rubro));
    }

    @Transactional
    public RubroAdminDTO setActivo(Long id, boolean activo) {
        RubroEmpleo rubro = find(id);
        rubro.setActivo(activo);
        return toDto(rubroEmpleoRepository.save(rubro));
    }

    private RubroEmpleo find(Long id) {
        return rubroEmpleoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rubro no encontrado"));
    }

    private String normalize(String value) {
        String nombre = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (nombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre del rubro es obligatorio");
        }
        return nombre;
    }

    private RubroAdminDTO toDto(RubroEmpleo rubro) {
        return new RubroAdminDTO(rubro.getId(), rubro.getNombreRubro(), rubro.isActivo());
    }
}
