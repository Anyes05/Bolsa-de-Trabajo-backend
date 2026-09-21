package uy.ccisj.api.postulante;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import uy.ccisj.api.domain.EstadoCivil;
import uy.ccisj.api.domain.Genero;
import uy.ccisj.api.oferta.Postulacion;
import uy.ccisj.api.user.User;

@Entity
@Table(name = "postulantes")
public class Postulante {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private User usuario;

    @Column(name = "nombre_completo", nullable = false, length = 160)
    private String nombreCompleto;

    @Column(name = "cedula_identidad", nullable = false, unique = true, length = 30)
    private String cedulaIdentidad;

    @Column(nullable = false, length = 40)
    private String telefono;

    @Column(name = "zona_residencia", nullable = false, length = 120)
    private String zonaResidencia;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Genero genero;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_civil", length = 20)
    private EstadoCivil estadoCivil;

    @OneToMany(mappedBy = "postulante", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PerfilLaboral> perfiles = new ArrayList<>();

    @OneToMany(mappedBy = "postulante", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Cv> cvs = new ArrayList<>();

    @OneToMany(mappedBy = "postulante")
    private List<Postulacion> postulaciones = new ArrayList<>();

    protected Postulante() {
    }

    public Postulante(User usuario, String nombreCompleto, String cedulaIdentidad, String telefono, String zonaResidencia) {
        this.usuario = usuario;
        this.nombreCompleto = nombreCompleto;
        this.cedulaIdentidad = cedulaIdentidad;
        this.telefono = telefono;
        this.zonaResidencia = zonaResidencia;
    }

    public Long getId() { return id; }
    public User getUsuario() { return usuario; }
    public void setUsuario(User usuario) { this.usuario = usuario; }
    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
    public String getCedulaIdentidad() { return cedulaIdentidad; }
    public void setCedulaIdentidad(String cedulaIdentidad) { this.cedulaIdentidad = cedulaIdentidad; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getZonaResidencia() { return zonaResidencia; }
    public void setZonaResidencia(String zonaResidencia) { this.zonaResidencia = zonaResidencia; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }
    public Genero getGenero() { return genero; }
    public void setGenero(Genero genero) { this.genero = genero; }
    public EstadoCivil getEstadoCivil() { return estadoCivil; }
    public void setEstadoCivil(EstadoCivil estadoCivil) { this.estadoCivil = estadoCivil; }
    public List<PerfilLaboral> getPerfiles() { return perfiles; }
    public List<Cv> getCvs() { return cvs; }
    public List<Postulacion> getPostulaciones() { return postulaciones; }
}
