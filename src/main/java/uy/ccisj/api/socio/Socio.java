package uy.ccisj.api.socio;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import uy.ccisj.api.catalogo.RubroEmpleo;
import uy.ccisj.api.domain.EstadoMorosidad;
import uy.ccisj.api.oferta.OfertaEmpleo;
import uy.ccisj.api.user.User;

@Entity
@Table(name = "socios")
public class Socio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private User usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direccion_id")
    private Direccion direccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubro_id")
    private RubroEmpleo rubro;

    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Column(unique = true, length = 20)
    private String rut;

    @Column(length = 40)
    private String telefono;

    @Column(name = "email_contacto", length = 254)
    private String emailContacto;

    @Column(length = 160)
    private String giro;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_morosidad", nullable = false, length = 30)
    private EstadoMorosidad estadoMorosidad = EstadoMorosidad.AL_DIA;

    @Column(name = "es_directivo", nullable = false)
    private boolean esDirectivo;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDate fechaAlta = LocalDate.now();

    @Column(name = "fecha_aniversario")
    private LocalDate fechaAniversario;

    @OneToMany(mappedBy = "socio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Cuota> cuotas = new ArrayList<>();

    @OneToMany(mappedBy = "socio")
    private List<OfertaEmpleo> ofertas = new ArrayList<>();

    protected Socio() {
    }

    public Socio(User usuario, String razonSocial) {
        this.usuario = usuario;
        this.razonSocial = razonSocial;
    }

    public Long getId() { return id; }
    public User getUsuario() { return usuario; }
    public void setUsuario(User usuario) { this.usuario = usuario; }
    public Direccion getDireccion() { return direccion; }
    public void setDireccion(Direccion direccion) { this.direccion = direccion; }
    public RubroEmpleo getRubro() { return rubro; }
    public void setRubro(RubroEmpleo rubro) { this.rubro = rubro; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getRut() { return rut; }
    public void setRut(String rut) { this.rut = rut; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getEmailContacto() { return emailContacto; }
    public void setEmailContacto(String emailContacto) { this.emailContacto = emailContacto; }
    public String getGiro() { return giro; }
    public void setGiro(String giro) { this.giro = giro; }
    public EstadoMorosidad getEstadoMorosidad() { return estadoMorosidad; }
    public void setEstadoMorosidad(EstadoMorosidad estadoMorosidad) { this.estadoMorosidad = estadoMorosidad; }
    public boolean isEsDirectivo() { return esDirectivo; }
    public void setEsDirectivo(boolean esDirectivo) { this.esDirectivo = esDirectivo; }
    public LocalDate getFechaAlta() { return fechaAlta; }
    public void setFechaAlta(LocalDate fechaAlta) { this.fechaAlta = fechaAlta; }
    public LocalDate getFechaAniversario() { return fechaAniversario; }
    public void setFechaAniversario(LocalDate fechaAniversario) { this.fechaAniversario = fechaAniversario; }
    public List<Cuota> getCuotas() { return cuotas; }
    public List<OfertaEmpleo> getOfertas() { return ofertas; }
}
