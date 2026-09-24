package uy.ccisj.api.postulante;

import java.time.OffsetDateTime;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import uy.ccisj.api.catalogo.HabilidadEstandar;

@Entity
@Table(name = "cvs")
public class Cv {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "postulante_id", nullable = false)
    private Postulante postulante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "perfil_laboral_id")
    private PerfilLaboral perfilLaboral;

    @Column(columnDefinition = "TEXT")
    private String resumen;

    @Column(name = "ruta_archivo_cv", length = 500)
    private String rutaArchivoCv;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "nombre_archivo", length = 255)
    private String nombreArchivo;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "fecha_carga", nullable = false)
    private OffsetDateTime fechaCarga;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

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
    public PerfilLaboral getPerfilLaboral() { return perfilLaboral; }
    public void setPerfilLaboral(PerfilLaboral perfilLaboral) { this.perfilLaboral = perfilLaboral; }
    public String getResumen() { return resumen; }
    public void setResumen(String resumen) { this.resumen = resumen; }
    public String getRutaArchivoCv() { return rutaArchivoCv; }
    public void setRutaArchivoCv(String rutaArchivoCv) { this.rutaArchivoCv = rutaArchivoCv; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public OffsetDateTime getFechaCarga() { return fechaCarga; }
    public void setFechaCarga(OffsetDateTime fechaCarga) { this.fechaCarga = fechaCarga; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public List<ExperienciaLaboral> getExperiencias() { return experiencias; }
    public List<FormacionAcademica> getFormaciones() { return formaciones; }
    public List<CvIdioma> getIdiomas() { return idiomas; }
    public Set<HabilidadEstandar> getHabilidades() { return habilidades; }
}
