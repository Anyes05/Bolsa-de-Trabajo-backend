package uy.ccisj.api.socio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SaldoCuenta {
    public record Cargo(String id, LocalDate fecha, LocalDate vencimiento, BigDecimal monto) {
    }

    public record Resultado(BigDecimal saldo, BigDecimal deudaVencida, Map<String, BigDecimal> pendientes) {
    }

    private SaldoCuenta() {
    }

    public static LocalDate vencimiento(LocalDate periodo) {
        return YearMonth.from(periodo).plusMonths(1).atEndOfMonth();
    }

    public static Resultado calcular(List<Cargo> cargos, BigDecimal pagos, LocalDate hoy) {
        BigDecimal creditoDisponible = pagos;
        BigDecimal deudaVencida = BigDecimal.ZERO;
        Map<String, BigDecimal> pendientes = new LinkedHashMap<>();
        List<Cargo> cargosOrdenados = cargos.stream()
            .sorted(Comparator.comparing(Cargo::fecha))
            .toList();
        for (Cargo cargo : cargosOrdenados) {
            BigDecimal montoAplicado = creditoDisponible.min(cargo.monto());
            BigDecimal pendiente = cargo.monto().subtract(montoAplicado);
            creditoDisponible = creditoDisponible.subtract(montoAplicado);
            pendientes.put(cargo.id(), pendiente);
            if (cargo.vencimiento().isBefore(hoy)) {
                deudaVencida = deudaVencida.add(pendiente);
            }
        }
        BigDecimal totalCargos = cargos.stream().map(Cargo::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Resultado(totalCargos.subtract(pagos), deudaVencida, pendientes);
    }
}