plugins {
    `java-library`
}

dependencies {
    implementation(project(":simulator-core"))

    // JSON parsing for planet library, glossary, NEO cache, save/load
    implementation("com.google.code.gson:gson:2.11.0")

    // HTTP client is built into Java 11+ so no extra dependency needed
    // Java's java.net.http.HttpClient will be used for NASA NeoWs API calls

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}