plugins {
    application
}

dependencies {
    implementation(project(":simulator-core"))
    implementation(project(":simulator-data"))
    implementation(project(":simulator-learning"))

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}