plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.camel")
    module.set("camel-core")
    versions.set("[3.7.0,3.8.0)")
    assertInverse.set(true)
  }
}

val camelversion = "3.9.0"

dependencies {
  library("org.apache.camel:camel-core:$camelversion")
  library("org.apache.camel:camel-api:$camelversion")

  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))

  testLibrary("org.apache.camel.springboot:camel-spring-boot-starter:$camelversion")
  testLibrary("org.apache.camel:camel-jetty:$camelversion")
  testLibrary("org.apache.camel:camel-http:$camelversion")
  testLibrary("org.apache.camel:camel-jaxb:$camelversion")
  testLibrary("org.apache.camel:camel-undertow:$camelversion")
  testLibrary("org.apache.camel:camel-cassandraql:$camelversion")

  testImplementation("org.testcontainers:localstack")
  testImplementation("org.testcontainers:cassandra")
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.apache-camel.3_7LTS=true")
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
