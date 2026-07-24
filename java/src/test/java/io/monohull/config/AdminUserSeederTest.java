package io.monohull.config;

import io.monohull.entity.AppUserEntity;
import io.monohull.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserSeederTest {

    @Mock private AppUserRepository users;
    @Mock private PasswordEncoder passwordEncoder;

    private AdminUserSeeder seeder(String username, String password) {
        return new AdminUserSeeder(users, passwordEncoder, username, password);
    }

    @Test
    void doesNothingWhenUsersAlreadyExist() {
        when(users.count()).thenReturn(1L);

        seeder("admin", "s3cret").run(null);

        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void seedsConfiguredCredentialsOnFirstBoot() {
        when(users.count()).thenReturn(0L);
        when(passwordEncoder.encode("s3cret")).thenReturn("{enc}s3cret");

        seeder("boss", "s3cret").run(null);

        ArgumentCaptor<AppUserEntity> saved = ArgumentCaptor.forClass(AppUserEntity.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("boss");
        assertThat(saved.getValue().getPassword()).isEqualTo("{enc}s3cret");
        assertThat(saved.getValue().getRole()).isEqualTo("ADMIN");
        assertThat(saved.getValue().isEnabled()).isTrue();
    }

    @Test
    void blankPasswordSeedsTheDocumentedDefaultNotADisabledAccount() {
        when(users.count()).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "{enc}" + inv.getArgument(0));

        seeder("admin", "").run(null);

        ArgumentCaptor<AppUserEntity> saved = ArgumentCaptor.forClass(AppUserEntity.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("{enc}changeme");
        assertThat(saved.getValue().isEnabled()).isTrue();
    }

    @Test
    void wellKnownPasswordStillSeeds() {
        when(users.count()).thenReturn(0L);
        when(passwordEncoder.encode("admin")).thenReturn("{enc}admin");

        seeder("admin", "admin").run(null);

        ArgumentCaptor<AppUserEntity> saved = ArgumentCaptor.forClass(AppUserEntity.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("{enc}admin");
    }
}
