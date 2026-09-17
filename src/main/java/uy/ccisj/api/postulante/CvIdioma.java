package uy.ccisj.api.postulante;

import java.io.Serializable;
import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import uy.ccisj.api.catalogo.Idioma;
import uy.ccisj.api.domain.NivelIdioma;

@Entity
@Table(name = "cv_idiomas")
public class CvIdioma {
    @EmbeddedId
    private CvIdiomaId id = new CvIdiomaId();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("cvId")
    @JoinColumn(name = "cv_id")
    private Cv cv;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("idiomaId")
    @JoinColumn(name = "idioma_id")
    private Idioma idioma;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NivelIdioma nivel;

    protected CvIdioma() {
    }

    public CvIdioma(Cv cv, Idioma idioma, NivelIdioma nivel) {
        this.cv = cv;
        this.idioma = idioma;
        this.nivel = nivel;
        this.id = new CvIdiomaId(cv.getId(), idioma.getId());
    }

    public CvIdiomaId getId() { return id; }
    public Cv getCv() { return cv; }
    public Idioma getIdioma() { return idioma; }
    public NivelIdioma getNivel() { return nivel; }
    public void setNivel(NivelIdioma nivel) { this.nivel = nivel; }

    @Embeddable
    public static class CvIdiomaId implements Serializable {
        @Column(name = "cv_id")
        private Long cvId;
        @Column(name = "idioma_id")
        private Long idiomaId;

        protected CvIdiomaId() {
        }

        public CvIdiomaId(Long cvId, Long idiomaId) {
            this.cvId = cvId;
            this.idiomaId = idiomaId;
        }

        public Long getCvId() { return cvId; }
        public Long getIdiomaId() { return idiomaId; }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CvIdiomaId that)) {
                return false;
            }
            return Objects.equals(cvId, that.cvId) && Objects.equals(idiomaId, that.idiomaId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cvId, idiomaId);
        }
    }
}
