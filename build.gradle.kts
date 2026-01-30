plugins {
    java
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "io.github.bearwatch-util"
version = "0.1.2"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenCentral()
}

dependencies {
    // HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON Serialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.assertj:assertj-core:3.25.0")
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    coordinates(group.toString(), "bearwatch-java-sdk", version.toString())

    pom {
        name.set("BearWatch Java SDK")
        description.set("Official Java SDK for BearWatch - Cron/Job Monitoring")
        url.set("https://github.com/bearwatch-util/java-sdk")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("bearwatch")
                name.set("BearWatch Team")
                email.set("support@bearwatch.dev")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/bearwatch-util/java-sdk.git")
            developerConnection.set("scm:git:ssh://github.com/bearwatch-util/java-sdk.git")
            url.set("https://github.com/bearwatch-util/java-sdk")
        }
    }
}
