package uy.ccisj.api.notificacion;

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
import uy.ccisj.api.comunicado.Comunicado;
import uy.ccisj.api.user.User;

@Entity
@Table(name = "notificaciones")
public class Notificacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunicado_id")
    private Comunicado comunicado;

    @Column(nullable = false, length = 180)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String cuerpo;

    @Column(nullable = false)
    private boolean leida;

    @Column(nullable = false)
    private boolean popup;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Notificacion() {
    }

    public Notificacion(User usuario, Comunicado comunicado, String titulo, String cuerpo, boolean popup) {
        this.usuario = usuario;
        this.comunicado = comunicado;
        this.titulo = titulo;
        this.cuerpo = cuerpo;
        this.leida = false;
        this.popup = popup;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public User getUsuario() { return usuario; }
    public Comunicado getComunicado() { return comunicado; }
    public String getTitulo() { return titulo; }
    public String getCuerpo() { return cuerpo; }
    public boolean isLeida() { return leida; }
    public void setLeida(boolean leida) { this.leida = leida; }
    public boolean isPopup() { return popup; }
    public void setPopup(boolean popup) { this.popup = popup; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
