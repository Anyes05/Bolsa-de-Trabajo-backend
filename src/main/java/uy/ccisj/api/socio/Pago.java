package uy.ccisj.api.socio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pagos")
public class Pago {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cuota_id", nullable = false, unique = true)
    private Cuota cuota;

    @Column(name = "monto_cobrado", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoCobrado;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    @Column(name = "nro_cobranza_externo", length = 80)
    private String nroCobranzaExterno;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "modificado_admin", nullable = false)
    private boolean modificadoAdmin;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Pago() {
    }

    public Pago(Cuota cuota, BigDecimal montoCobrado, LocalDate fechaEmision) {
        this.cuota = cuota;
        this.montoCobrado = montoCobrado;
        this.fechaEmision = fechaEmision;
    }

    public Long getId() { return id; }
    public Cuota getCuota() { return cuota; }
    public void setCuota(Cuota cuota) { this.cuota = cuota; }
    public BigDecimal getMontoCobrado() { return montoCobrado; }
    public void setMontoCobrado(BigDecimal montoCobrado) { this.montoCobrado = montoCobrado; }
    public LocalDate getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDate fechaEmision) { this.fechaEmision = fechaEmision; }
    public String getNroCobranzaExterno() { return nroCobranzaExterno; }
    public void setNroCobranzaExterno(String nroCobranzaExterno) { this.nroCobranzaExterno = nroCobranzaExterno; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public boolean isModificadoAdmin() { return modificadoAdmin; }
    public void setModificadoAdmin(boolean modificadoAdmin) { this.modificadoAdmin = modificadoAdmin; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
