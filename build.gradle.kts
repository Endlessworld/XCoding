plugins {
    kotlin("jvm") version "2.2.20" apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
    id("org.graalvm.buildtools.native") version "0.10.2" apply false
}
