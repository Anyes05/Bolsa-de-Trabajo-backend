package uy.ccisj.api.postulante;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import uy.ccisj.api.catalogo.HabilidadEstandar;

@Entity
@Table(name = "cvs")
public class Cv {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "postulante_id", nullable = false, unique = true)
    private Postulante postulante;

    @Column(columnDefinition = "TEXT")
    private String resumen;

    @Column(name = "ruta_archivo_cv", length = 500)
    private String rutaArchivoCv;

    @OneToMany(mappedBy = "cv", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExperienciaLaboral> experiencias = new ArrayList<>();

    @OneToMany(mappedBy = "cv", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FormacionAcademica> formaciones = new ArrayList<>();

    @OneToMany(mappedBy = "cv", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CvIdioma> idiomas = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "cv_habilidades",
            joinColumns = @JoinColumn(name = "cv_id"),
            inverseJoinColumns = @JoinColumn(name = "habilidad_id"))
    private Set<HabilidadEstandar> habilidades = new HashSet<>();

    protected Cv() {
    }

    public Cv(Postulante postulante) {
        this.postulante = postulante;
    }

    public Long getId() { return id; }
    public Postulante getPostulante() { return postulante; }
    public void setPostulante(Postulante postulante) { this.postulante = postulante; }
    public String getResumen() { return resumen; }
    public void setResumen(String resumen) { this.resumen = resumen; }
    public String getRutaArchivoCv() { return rutaArchivoCv; }
    public void setRutaArchivoCv(String rutaArchivoCv) { this.rutaArchivoCv = rutaArchivoCv; }
    public List<ExperienciaLaboral> getExperiencias() { return experiencias; }
    public List<FormacionAcademica> getFormaciones() { return formaciones; }
    public List<CvIdioma> getIdiomas() { return idiomas; }
    public Set<HabilidadEstandar> getHabilidades() { return habilidades; }
}
