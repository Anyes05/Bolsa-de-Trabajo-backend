package uy.ccisj.api.socio;

import jakarta.persistence.EntityManager;
import org.springframework.security.core.context.SecurityContextHolder;

final class ContabilidadContext {
    private ContabilidadContext() {}

    static void registrar(EntityManager em, String motivo) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String autor = auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
                ? "ADMIN:" + auth.getName()
                : motivo.startsWith("Facturación del período ") ? "JOB:FACTURACION" : "SISTEMA:APLICACION";
        em.createNativeQuery("SELECT set_config('app.contable_autor', :autor, true), "
                        + "set_config('app.contable_motivo', :motivo, true)")
                .setParameter("autor", autor).setParameter("motivo", motivo).getSingleResult();
    }
}
