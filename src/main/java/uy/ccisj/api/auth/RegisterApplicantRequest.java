package uy.ccisj.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RegisterApplicantRequest(
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Size(max = 30) String identityCard,
        @NotBlank @Size(max = 40) String phone,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 120) String residenceArea,
        @NotBlank @Size(max = 120) String profileName,
        @NotEmpty List<@NotBlank String> sectors,
        @Pattern(regexp = "FULL_TIME|PART_TIME") String availability,
        boolean hasVehicle,
        @Size(max = 200) String latestJob,
        @Size(max = 4000) String experienceDescription) {
}