package uy.ccisj.api.socio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uy.ccisj.api.user.Role;
import uy.ccisj.api.user.User;
import uy.ccisj.api.user.UserRepository;

class SocioPhotoTest {
    @Test
    void returnsEachAccountsOwnPhotoInAdminListAndSocioProfile() {
        var socios = mock(SocioRepository.class);
        var users = mock(UserRepository.class);
        var service = new SocioService(socios, users, null, null, null, null, null, "https://example.test");
        User firstUser = mock(User.class);
        when(firstUser.getId()).thenReturn(10L);
        when(firstUser.getEmail()).thenReturn("first@example.test");
        when(firstUser.getRole()).thenReturn(Role.SOCIO);
        when(firstUser.isActivo()).thenReturn(true);
        when(firstUser.getFotoPerfil()).thenReturn(new byte[] {1, 2, 3});
        when(firstUser.getFotoMime()).thenReturn("image/png");
        Socio first = new Socio(firstUser, "Primera empresa");
        User secondUser = mock(User.class);
        when(secondUser.getFotoPerfil()).thenReturn(new byte[] {4});
        when(secondUser.getFotoMime()).thenReturn("image/webp");
        Socio second = new Socio(secondUser, "Segunda empresa");
        Socio withoutPhoto = new Socio(new User("third@example.test", null, "test-only", Role.SOCIO), "Sin foto");
        when(socios.findAllByOrderByRazonSocialAsc()).thenReturn(List.of(first, second, withoutPhoto));
        when(users.findByEmailIgnoreCase("first@example.test")).thenReturn(Optional.of(firstUser));
        when(socios.findByUsuarioId(10L)).thenReturn(Optional.of(first));

        var list = service.getAllSocios();
        assertEquals("data:image/png;base64,AQID", list.get(0).fotoUrl());
        assertEquals("data:image/webp;base64,BA==", list.get(1).fotoUrl());
        assertFalse(list.get(1).activo());
        assertNull(list.get(2).fotoUrl());
        assertEquals(list.get(0).fotoUrl(), service.getMiPerfil("first@example.test").fotoUrl());
        when(firstUser.getFotoPerfil()).thenReturn(null);
        when(firstUser.getFotoMime()).thenReturn(null);
        assertNull(service.getMiPerfil("first@example.test").fotoUrl());
    }
}
