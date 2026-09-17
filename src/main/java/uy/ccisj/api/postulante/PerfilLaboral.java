package uy.ccisj.api.postulante;

import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.domain.DisponibilidadHoraria;

@Entity
@Table(name = "perfiles_laborales")
public class PerfilLaboral {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "postulante_id", nullable = false)
    private Postulante postulante;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "disponibilidad_horaria", nullable = false, length = 20)
    private DisponibilidadHoraria disponibilidadHoraria;

    @Column(name = "tiene_vehiculo", nullable = false)
    private boolean tieneVehiculo;

    @Column(name = "ultimo_empleo", length = 200)
    private String ultimoEmpleo;

    @Column(name = "descripcion_experiencia", columnDefinition = "TEXT")
    private String descripcionExperiencia;

    @ManyToMany
    @JoinTable(
            name = "perfil_rubros",
            joinColumns = @JoinColumn(name = "perfil_id"),
            inverseJoinColumns = @JoinColumn(name = "rubro_id"))
    private Set<RubroEmpleo> rubros = new HashSet<>();

    protected PerfilLaboral() {
    }

    public PerfilLaboral(Postulante postulante, String nombre, DisponibilidadHoraria disponibilidadHoraria) {
        this.postulante = postulante;
        this.nombre = nombre;
        this.disponibilidadHoraria = disponibilidadHoraria;
    }

    public Long getId() { return id; }
    public Postulante getPostulante() { return postulante; }
    public void setPostulante(Postulante postulante) { this.postulante = postulante; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public DisponibilidadHoraria getDisponibilidadHoraria() { return disponibilidadHoraria; }
    public void setDisponibilidadHoraria(DisponibilidadHoraria disponibilidadHoraria) { this.disponibilidadHoraria = disponibilidadHoraria; }
    public boolean isTieneVehiculo() { return tieneVehiculo; }
    public void setTieneVehiculo(boolean tieneVehiculo) { this.tieneVehiculo = tieneVehiculo; }
    public String getUltimoEmpleo() { return ultimoEmpleo; }
    public void setUltimoEmpleo(String ultimoEmpleo) { this.ultimoEmpleo = ultimoEmpleo; }
    public String getDescripcionExperiencia() { return descripcionExperiencia; }
    public void setDescripcionExperiencia(String descripcionExperiencia) { this.descripcionExperiencia = descripcionExperiencia; }
    public Set<RubroEmpleo> getRubros() { return rubros; }
}
