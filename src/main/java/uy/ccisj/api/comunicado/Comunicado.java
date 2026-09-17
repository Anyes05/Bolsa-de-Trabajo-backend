package uy.ccisj.api.comunicado;

import java.time.OffsetDateTime;
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
import jakarta.persistence.Table;
import uy.ccisj.api.domain.DestinatarioComunicado;
import uy.ccisj.api.user.User;

@Entity
@Table(name = "comunicados")
public class Comunicado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id", nullable = false)
    private User autor;

    @Column(nullable = false, length = 180)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "fecha_publicacion", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime fechaPublicacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DestinatarioComunicado destinatario = DestinatarioComunicado.TODOS;

    protected Comunicado() {
    }

    public Comunicado(User autor, String titulo, String contenido, DestinatarioComunicado destinatario) {
        this.autor = autor;
        this.titulo = titulo;
        this.contenido = contenido;
        this.destinatario = destinatario;
    }

    public Long getId() { return id; }
    public User getAutor() { return autor; }
    public void setAutor(User autor) { this.autor = autor; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    public OffsetDateTime getFechaPublicacion() { return fechaPublicacion; }
    public DestinatarioComunicado getDestinatario() { return destinatario; }
    public void setDestinatario(DestinatarioComunicado destinatario) { this.destinatario = destinatario; }
}
