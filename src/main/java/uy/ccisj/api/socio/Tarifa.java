package uy.ccisj.api.socio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "tarifas", uniqueConstraints = @UniqueConstraint(columnNames = "anio"))
public class Tarifa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int anio;

    @Column(name = "monto_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoBase;

    @Column(name = "dia_vencimiento", nullable = false)
    private int diaVencimiento;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Tarifa() {
    }

    public Tarifa(int anio, BigDecimal montoBase, int diaVencimiento) {
        this.anio = anio;
        this.montoBase = montoBase;
        this.diaVencimiento = diaVencimiento;
    }

    public Long getId() { return id; }
    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }
    public BigDecimal getMontoBase() { return montoBase; }
    public void setMontoBase(BigDecimal montoBase) { this.montoBase = montoBase; }
    public int getDiaVencimiento() { return diaVencimiento; }
    public void setDiaVencimiento(int diaVencimiento) { this.diaVencimiento = diaVencimiento; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}