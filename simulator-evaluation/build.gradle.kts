plugins {
    `java-library`
}

group = "io.github.eugenp84.orbitalsim"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(project(":simulator-core"))
    testImplementation(project(":simulator-data"))
    testImplementation("com.google.code.gson:gson:2.11.0")

}

tasks.test {
    useJUnitPlatform()
}