package uy.ccisj.api.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GoogleRegistrationRequest(
        @NotBlank @Size(max = 8192) String registrationToken,
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Size(max = 30) String identityCard,
        @NotBlank @Size(max = 40) String phone,
        @NotBlank @Size(max = 120) String residenceArea,
        @NotBlank @Size(max = 120) String profileName,
        @NotEmpty @Size(max = 4) List<@NotBlank String> sectors,
        @NotBlank @Pattern(regexp = "FULL_TIME|PART_TIME") String availability,
        boolean hasVehicle,
        @Size(max = 200) String latestJob,
        @Size(max = 4000) String experienceDescription,
        @AssertTrue(message = "Debés aceptar los términos y condiciones") boolean acceptedTerms) {}
