package dev.liamnet.rung.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.liamnet.rung.MigrationEngine;
import dev.liamnet.rung.internal.MigrationLayout.Location;
import org.junit.jupiter.api.Test;

class MigrationLayoutTests {

    @Test
    void defaultLayoutFindsMigrationsNextToTheModule() {
        final MigrationLayout layout = new MigrationLayout(MigrationEngine.DEFAULT_LAYOUT);

        assertThat(layout.locate("user/migrations/V001__create_user.sql"))
                .contains(new Location("user", "V001__create_user.sql"));
        assertThat(layout.locate("user/V001__create_user.sql")).isEmpty();
        assertThat(layout.locate("user/internal/migrations/V001__create_user.sql"))
                .isEmpty();
    }

    @Test
    void moduleOnlyLayoutTreatsEachFolderAsAModule() {
        final MigrationLayout layout = new MigrationLayout("{module}");

        assertThat(layout.locate("user/V001__create_user.sql")).contains(new Location("user", "V001__create_user.sql"));
        assertThat(layout.locate("V001__create_user.sql")).isEmpty();
    }

    @Test
    void placeholderCanSitAnywhereInTheLayout() {
        final MigrationLayout layout = new MigrationLayout("/db/{module}/sql/");

        assertThat(layout.locate("db/user/sql/V001__create_user.sql"))
                .contains(new Location("user", "V001__create_user.sql"));
        assertThat(layout.locate("db/user/V001__create_user.sql")).isEmpty();
    }

    @Test
    void rejectsALayoutWithoutExactlyOnePlaceholder() {
        assertThatThrownBy(() -> new MigrationLayout("migrations"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{module} exactly once");
        assertThatThrownBy(() -> new MigrationLayout("{module}/{module}")).isInstanceOf(IllegalArgumentException.class);
    }
}
