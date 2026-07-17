plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

dependencies {
    implementation(project(":simulator-core"))
    implementation(project(":simulator-data"))
    implementation(project(":simulator-learning"))

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

javafx {
    version = "21"
    modules = listOf(
        "javafx.controls",
        "javafx.fxml"
    )
}

application {
    mainClass.set("io.github.eugenp84.orbitalsim.app.Main")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}