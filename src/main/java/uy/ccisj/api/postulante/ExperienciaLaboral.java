package uy.ccisj.api.postulante;

import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "experiencias_laborales")
public class ExperienciaLaboral {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cv_id", nullable = false)
    private Cv cv;

    @Column(name = "nombre_empresa", nullable = false, length = 160)
    private String nombreEmpresa;

    @Column(nullable = false, length = 120)
    private String cargo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(columnDefinition = "TEXT")
    private String tareas;

    @Column(name = "contacto_referencia", length = 160)
    private String contactoReferencia;

    protected ExperienciaLaboral() {
    }

    public ExperienciaLaboral(Cv cv, String nombreEmpresa, String cargo, LocalDate fechaInicio) {
        this.cv = cv;
        this.nombreEmpresa = nombreEmpresa;
        this.cargo = cargo;
        this.fechaInicio = fechaInicio;
    }

    public Long getId() { return id; }
    public Cv getCv() { return cv; }
    public void setCv(Cv cv) { this.cv = cv; }
    public String getNombreEmpresa() { return nombreEmpresa; }
    public void setNombreEmpresa(String nombreEmpresa) { this.nombreEmpresa = nombreEmpresa; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public String getTareas() { return tareas; }
    public void setTareas(String tareas) { this.tareas = tareas; }
    public String getContactoReferencia() { return contactoReferencia; }
    public void setContactoReferencia(String contactoReferencia) { this.contactoReferencia = contactoReferencia; }
}
