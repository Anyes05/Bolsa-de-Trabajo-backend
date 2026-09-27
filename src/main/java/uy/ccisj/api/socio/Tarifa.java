package uy.ccisj.api.socio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "tarifas", uniqueConstraints = @UniqueConstraint(columnNames = "periodo"))
public class Tarifa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate periodo;

    @Column(name = "monto_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoBase;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Tarifa() {
    }

    public Tarifa(LocalDate periodo, BigDecimal montoBase, LocalDate fechaVencimiento) {
        this.periodo = periodo;
        this.montoBase = montoBase;
        this.fechaVencimiento = fechaVencimiento;
    }

    public Long getId() { return id; }
    public LocalDate getPeriodo() { return periodo; }
    public void setPeriodo(LocalDate periodo) { this.periodo = periodo; }
    public BigDecimal getMontoBase() { return montoBase; }
    public void setMontoBase(BigDecimal montoBase) { this.montoBase = montoBase; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}