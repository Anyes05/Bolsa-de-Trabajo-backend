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
@Table(name = "reintegros")
public class Reintegro {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id", nullable = false)
    private Socio socio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuota_id")
    private Cuota cuota;

    @Column(nullable = false, length = 200)
    private String concepto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(unique = true, length = 120)
    private String origen;

    protected Reintegro() {
    }

    public Reintegro(Socio socio, String concepto, BigDecimal monto, LocalDate fecha,
                     LocalDate fechaVencimiento, String origen) {
        this.socio = socio;
        this.concepto = concepto;
        this.monto = monto;
        this.fecha = fecha;
        this.fechaVencimiento = fechaVencimiento;
        this.origen = origen;
    }

    public Long getId() { return id; }
    public Socio getSocio() { return socio; }
    public Cuota getCuota() { return cuota; }
    public String getConcepto() { return concepto; }
    public BigDecimal getMonto() { return monto; }
    public LocalDate getFecha() { return fecha; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public String getOrigen() { return origen; }
}