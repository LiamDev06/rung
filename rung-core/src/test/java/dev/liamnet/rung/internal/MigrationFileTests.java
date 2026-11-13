package dev.liamnet.rung.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MigrationFileTests {

    @Test
    void parsesVersionAndUpAndDownSections() {
        final String content = "-- +migrate up\n"
                + "CREATE TABLE widget (id int);\n"
                + "\n"
                + "-- +migrate down\n"
                + "DROP TABLE widget;\n";

        final MigrationFile migration = MigrationFile.parse("V001__create_widget.sql", content);

        assertThat(migration.version()).isEqualTo("001");
        assertThat(migration.script()).isEqualTo("V001__create_widget.sql");
        assertThat(migration.upSql()).isEqualTo("CREATE TABLE widget (id int);");
        assertThat(migration.downSql()).isEqualTo("DROP TABLE widget;");
        assertThat(migration.hasDown()).isTrue();
    }

    @Test
    void treatsAMissingDownSectionAsNotRollbackable() {
        final MigrationFile migration =
                MigrationFile.parse("V002__add_only.sql", "-- +migrate up\nALTER TABLE widget ADD COLUMN x int;\n");

        assertThat(migration.hasDown()).isFalse();
        assertThat(migration.downSql()).isEmpty();
    }

    @Test
    void rejectsAFileWithoutAnUpSection() {
        assertThatThrownBy(() -> MigrationFile.parse("V001__x.sql", "-- +migrate down\nDROP TABLE widget;\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no '-- +migrate up' section");
    }

    @Test
    void markersMustBeLowercase() {
        assertThatThrownBy(() -> MigrationFile.parse("V001__x.sql", "-- +migrate Up\nSELECT 1;\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no '-- +migrate up' section");
    }

    @Test
    void rejectsAFileNameThatIsNotVersioned() {
        assertThatThrownBy(() -> MigrationFile.parse("create_widget.sql", "-- +migrate up\nSELECT 1;\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("V<version>__<description>.sql");
    }

    @Test
    void checksumTracksTheUpBlockButIgnoresTheDown() {
        final MigrationFile original = MigrationFile.parse(
                "V001__x.sql", "-- +migrate up\nCREATE TABLE a (id int);\n-- +migrate down\nDROP TABLE a;\n");
        final MigrationFile editedDown = MigrationFile.parse(
                "V001__x.sql", "-- +migrate up\nCREATE TABLE a (id int);\n-- +migrate down\nDROP TABLE a CASCADE;\n");
        final MigrationFile editedUp = MigrationFile.parse(
                "V001__x.sql", "-- +migrate up\nCREATE TABLE b (id int);\n-- +migrate down\nDROP TABLE a;\n");

        assertThat(editedDown.checksum()).isEqualTo(original.checksum());
        assertThat(editedUp.checksum()).isNotEqualTo(original.checksum());
    }

    @Test
    void checksumIgnoresLineEndingStyle() {
        final MigrationFile lfFile = MigrationFile.parse("V001__x.sql", "-- +migrate up\nCREATE TABLE a (id int);\n");
        final MigrationFile crlfFile =
                MigrationFile.parse("V001__x.sql", "-- +migrate up\r\nCREATE TABLE a (id int);\r\n");

        assertThat(crlfFile.checksum()).isEqualTo(lfFile.checksum());
    }
}
