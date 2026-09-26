package uy.ccisj.api.user;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import uy.ccisj.api.postulante.Postulante;
import uy.ccisj.api.socio.Socio;

@Entity
@Table(name = "usuarios")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 254)
    private String email;

    @Column(unique = true, length = 30)
    private String bps;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "fecha_baja")
    private OffsetDateTime fechaBaja;

    @OneToOne(mappedBy = "usuario", fetch = FetchType.LAZY)
    private Postulante postulante;

    @OneToOne(mappedBy = "usuario", fetch = FetchType.LAZY)
    private Socio socio;

    protected User() {
    }

    public User(String email, String bps, String passwordHash, Role role) {
        this.email = email;
        this.bps = bps;
        this.passwordHash = passwordHash;
        this.role = role;
        this.activo = true;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getBps() { return bps; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public OffsetDateTime getFechaBaja() { return fechaBaja; }
    public void setFechaBaja(OffsetDateTime fechaBaja) { this.fechaBaja = fechaBaja; }
    public Postulante getPostulante() { return postulante; }
    public Socio getSocio() { return socio; }
}
