/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.Session
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.RetryOnAddressAlreadyInUseTrait
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class CassandraTest extends AgentInstrumentationSpecification implements RetryOnAddressAlreadyInUseTrait {

  @Shared
  ConfigurableApplicationContext server
  @Shared
  GenericContainer cassandra
  @Shared
  Cluster cluster
  @Shared
  String host
  @Shared
  int port

  Session session

  def setupSpec() {
    withRetryOnAddressAlreadyInUse({
      setupSpecUnderRetry()
    })
  }

  def setupSpecUnderRetry() {
    cassandra = new CassandraContainer()
    cassandra.withExposedPorts(9042)
    cassandra.start()

    port = cassandra.getFirstMappedPort()
    host = cassandra.getHost()

    cluster = cassandra.getCluster()

    def app = new SpringApplication(CassandraConfig)
    app.setDefaultProperties(["cassandra.host": host, "cassandra.port": port])
    server = app.run()
  }

  def cleanupSpec() {
    server?.close()
    cluster?.close()
    cassandra.stop()
  }

  def setup() {
    session = cluster.connect()

    session.execute("CREATE KEYSPACE IF NOT EXISTS test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1};")
    session.execute("CREATE TABLE IF NOT EXISTS test.users ( id int primary key, name text );")
    session.execute("INSERT INTO test.users (id,name) VALUES (1, 'user1') IF NOT EXISTS;")
    session.execute("INSERT INTO test.users (id,name) VALUES (2, 'user2') IF NOT EXISTS;")
  }

  def cleanup() {
    session?.close()
  }

  def "test cassandra "() {

    setup:
    def camelContext = server.getBean(CamelContext)
    ProducerTemplate template = camelContext.createProducerTemplate()

    when:
    template.asyncRequestBody("direct:input", null)

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          kind CLIENT
          hasNoParent()
          attributes {
            "camel.uri" "direct://input"
            "component"  "camel-direct"
          }
        }
        span(1) {
          kind CLIENT
          attributes {
            "camel.uri" "direct://input"
            "component"  "camel-direct"
          }
        }
        span(2) {
          kind INTERNAL
          attributes {
            "camel.uri" "direct://input"
            "component"  "camel-direct"
          }
        }
        span(3) {
          kind CLIENT
          attributes {
            "camel.uri" String
            "component"  "camel-cql"
          }
        }
      }
    }

  }

}
