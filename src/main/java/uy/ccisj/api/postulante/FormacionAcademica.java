package uy.ccisj.api.postulante;

import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import uy.ccisj.api.domain.NivelEstudio;

@Entity
@Table(name = "formaciones_academicas")
public class FormacionAcademica {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cv_id", nullable = false)
    private Cv cv;

    @Column(nullable = false, length = 160)
    private String institucion;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NivelEstudio nivel;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "en_curso", nullable = false)
    private boolean enCurso;

    protected FormacionAcademica() {
    }

    public FormacionAcademica(Cv cv, String institucion, String titulo, NivelEstudio nivel) {
        this.cv = cv;
        this.institucion = institucion;
        this.titulo = titulo;
        this.nivel = nivel;
    }

    public Long getId() { return id; }
    public Cv getCv() { return cv; }
    public void setCv(Cv cv) { this.cv = cv; }
    public String getInstitucion() { return institucion; }
    public void setInstitucion(String institucion) { this.institucion = institucion; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public NivelEstudio getNivel() { return nivel; }
    public void setNivel(NivelEstudio nivel) { this.nivel = nivel; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public boolean isEnCurso() { return enCurso; }
    public void setEnCurso(boolean enCurso) { this.enCurso = enCurso; }
}
