package uy.ccisj.api.socio;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import uy.ccisj.api.domain.EstadoCuota;

@Entity
@Table(name = "cuotas", uniqueConstraints = @UniqueConstraint(columnNames = {"socio_id", "periodo"}))
public class Cuota {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id", nullable = false)
    private Socio socio;

    @Column(nullable = false)
    private LocalDate periodo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCuota estado = EstadoCuota.PENDIENTE;

    @OneToOne(mappedBy = "cuota", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Pago pago;

    protected Cuota() {
    }

    public Cuota(Socio socio, LocalDate periodo, BigDecimal monto, LocalDate fechaVencimiento) {
        this.socio = socio;
        this.periodo = periodo;
        this.monto = monto;
        this.fechaVencimiento = fechaVencimiento;
    }

    public Long getId() { return id; }
    public Socio getSocio() { return socio; }
    public void setSocio(Socio socio) { this.socio = socio; }
    public LocalDate getPeriodo() { return periodo; }
    public void setPeriodo(LocalDate periodo) { this.periodo = periodo; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
    public EstadoCuota getEstado() { return estado; }
    public void setEstado(EstadoCuota estado) { this.estado = estado; }
    public Pago getPago() { return pago; }
    public void setPago(Pago pago) { this.pago = pago; }
}
