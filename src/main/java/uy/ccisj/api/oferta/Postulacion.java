package uy.ccisj.api.oferta;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import uy.ccisj.api.postulante.Postulante;

@Entity
@Table(name = "postulaciones", uniqueConstraints = @UniqueConstraint(columnNames = {"postulante_id", "oferta_id"}))
public class Postulacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "postulante_id", nullable = false)
    private Postulante postulante;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "oferta_id", nullable = false)
    private OfertaEmpleo oferta;

    @Column(nullable = false, insertable = false, updatable = false)
    private OffsetDateTime fecha;

    @Column(name = "rubro_principal", nullable = false)
    private boolean rubroPrincipal;

    protected Postulacion() {
    }

    public Postulacion(Postulante postulante, OfertaEmpleo oferta) {
        this.postulante = postulante;
        this.oferta = oferta;
    }

    public Long getId() { return id; }
    public Postulante getPostulante() { return postulante; }
    public void setPostulante(Postulante postulante) { this.postulante = postulante; }
    public OfertaEmpleo getOferta() { return oferta; }
    public void setOferta(OfertaEmpleo oferta) { this.oferta = oferta; }
    public OffsetDateTime getFecha() { return fecha; }
    public boolean isRubroPrincipal() { return rubroPrincipal; }
    public void setRubroPrincipal(boolean rubroPrincipal) { this.rubroPrincipal = rubroPrincipal; }
}
