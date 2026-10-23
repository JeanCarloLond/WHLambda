plugins {
    alias(libs.plugins.whlambda.kmp.library)
}

kotlin {
    // Pure domain logic: every public declaration must be intentional.
    explicitApi()
}
