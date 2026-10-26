plugins {
    alias(libs.plugins.whlambda.kmp.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            // SqlDriver and the generated database are part of this module's API: the apps provide the driver.
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        // Both host test runs are JVMs, so they share the JDBC SQLite driver for an in-memory database.
        getByName("desktopTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

sqldelight {
    databases {
        create("WhDatabase") {
            packageName.set("com.waveheaven.lambda.core.data.db")
            // The schema is the result of applying the numbered migrations (1.sqm, 2.sqm…) next to the queries.
            deriveSchemaFromMigrations.set(true)
        }
    }
}
