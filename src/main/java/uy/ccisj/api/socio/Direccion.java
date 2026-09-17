package uy.ccisj.api.socio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "direcciones")
public class Direccion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String calle;

    @Column(length = 20)
    private String numero;

    @Column(nullable = false, length = 120)
    private String localidad;

    protected Direccion() {
    }

    public Direccion(String calle, String numero, String localidad) {
        this.calle = calle;
        this.numero = numero;
        this.localidad = localidad;
    }

    public Long getId() { return id; }
    public String getCalle() { return calle; }
    public void setCalle(String calle) { this.calle = calle; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getLocalidad() { return localidad; }
    public void setLocalidad(String localidad) { this.localidad = localidad; }
}
