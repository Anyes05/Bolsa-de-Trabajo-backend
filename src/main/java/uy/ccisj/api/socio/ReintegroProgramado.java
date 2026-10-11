package uy.ccisj.api.socio;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "reintegros_programados")
public class ReintegroProgramado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id", nullable = false)
    private Socio socio;

    @Column(nullable = false, length = 200)
    private String concepto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "desde_periodo", nullable = false)
    private LocalDate desdePeriodo;

    protected ReintegroProgramado() {
    }

    public ReintegroProgramado(Socio socio, String concepto, BigDecimal monto, LocalDate desdePeriodo) {
        this.socio = socio;
        this.concepto = concepto;
        this.monto = monto;
        this.desdePeriodo = desdePeriodo;
    }

    public Long getId() { return id; }
    public Socio getSocio() { return socio; }
    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public LocalDate getDesdePeriodo() { return desdePeriodo; }
    public void setDesdePeriodo(LocalDate desdePeriodo) { this.desdePeriodo = desdePeriodo; }
}