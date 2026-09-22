package uy.ccisj.api.oferta;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.domain.DisponibilidadHoraria;
import uy.ccisj.api.domain.EstadoOferta;
import uy.ccisj.api.domain.TipoContrato;
import uy.ccisj.api.socio.Socio;

@Entity
@Table(name = "ofertas_empleo")
public class OfertaEmpleo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    //
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id", nullable = false)
    private Socio socio;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "rubro_id", nullable = false)
    private RubroEmpleo rubro;

    @Column(nullable = false, length = 180)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(length = 120)
    private String cargo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_contrato", length = 20)
    private TipoContrato tipoContrato;

    @Enumerated(EnumType.STRING)
    @Column(name = "disponibilidad_horaria", nullable = false, length = 20)
    private DisponibilidadHoraria disponibilidadHoraria = DisponibilidadHoraria.FULL_TIME;

    @Column(nullable = false)
    private int vacantes = 1;

    @Column(name = "fecha_publicacion", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime fechaPublicacion;

    @Column(name = "fecha_cierre")
    private LocalDate fechaCierre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOferta estado = EstadoOferta.ACTIVA;

    @OneToMany(mappedBy = "oferta")
    private List<Postulacion> postulaciones = new ArrayList<>();

    protected OfertaEmpleo() {
    }

    public OfertaEmpleo(Socio socio, RubroEmpleo rubro, String titulo, String descripcion) {
        this.socio = socio;
        this.rubro = rubro;
        this.titulo = titulo;
        this.descripcion = descripcion;
    }

    public Long getId() { return id; }
    public Socio getSocio() { return socio; }
    public void setSocio(Socio socio) { this.socio = socio; }
    public RubroEmpleo getRubro() { return rubro; }
    public void setRubro(RubroEmpleo rubro) { this.rubro = rubro; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public TipoContrato getTipoContrato() { return tipoContrato; }
    public void setTipoContrato(TipoContrato tipoContrato) { this.tipoContrato = tipoContrato; }
    public DisponibilidadHoraria getDisponibilidadHoraria() { return disponibilidadHoraria; }
    public void setDisponibilidadHoraria(DisponibilidadHoraria disponibilidadHoraria) { this.disponibilidadHoraria = disponibilidadHoraria; }
    public int getVacantes() { return vacantes; }
    public void setVacantes(int vacantes) { this.vacantes = vacantes; }
    public OffsetDateTime getFechaPublicacion() { return fechaPublicacion; }
    public LocalDate getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDate fechaCierre) { this.fechaCierre = fechaCierre; }
    public EstadoOferta getEstado() { return estado; }
    public void setEstado(EstadoOferta estado) { this.estado = estado; }
    public List<Postulacion> getPostulaciones() { return postulaciones; }
}
