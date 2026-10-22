package uy.ccisj.api.socio.dto;

public record CredencialesEnviadasDTO(
        String destinatario,
        String passwordProvisional,
        String mensaje) {
}
