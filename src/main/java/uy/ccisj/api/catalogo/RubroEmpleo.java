package uy.ccisj.api.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rubros_empleo")
public class RubroEmpleo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_rubro", nullable = false, unique = true, length = 100)
    private String nombreRubro;

    @Column(nullable = false)
    private boolean activo = true;

    protected RubroEmpleo() {
    }

    public RubroEmpleo(String nombreRubro) {
        this.nombreRubro = nombreRubro;
    }

    public Long getId() { return id; }
    public String getNombreRubro() { return nombreRubro; }
    public void setNombreRubro(String nombreRubro) { this.nombreRubro = nombreRubro; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
