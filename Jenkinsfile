node {
  def builds = [:]
  builds['Build JDK 11 - Jetty 9.2.x'] = getBuild('9.2.29.v20191105', true)
  builds['Build JDK 11 - Jetty 9.3.x'] = getBuild('9.3.28.v20191105', false)
  builds['Build JDK 11 - Jetty 9.4.x'] = getBuild('9.4.30.v20200611', false)
  parallel builds
}

def getBuild(jettyVersion, mainBuild) {
  return {
    node("linux") {
      def jdk = 'jdk11'
      def mvnName = 'maven3.5'
      def settingsName = 'oss-settings.xml'
      def mvnOpts = '-Xms1g -Xmx1g -Djava.awt.headless=true'

      stage('Checkout') {
        checkout scm
      }

      stage("Build ${jettyVersion}") {
        timeout(time: 1, unit: 'HOURS') {
          if (mainBuild) {
            withMaven(maven: mvnName,
                    jdk: jdk,
                    publisherStrategy: 'EXPLICIT',
                    globalMavenSettingsConfig: settingsName,
                    mavenOpts: mvnOpts) {
              sh "mvn -V -B clean install -Dmaven.test.failure.ignore=true -e"
            }
          } else {
            withMaven(maven: mvnName,
                    jdk: jdk,
                    publisherStrategy: 'EXPLICIT',
                    globalMavenSettingsConfig: settingsName,
                    mavenOpts: mvnOpts) {
              sh "mvn -V -B clean install -Dmaven.test.failure.ignore=true -e -Djetty-version=${jettyVersion}"
            }
          }

          junit testResults: '**/target/surefire-reports/TEST-*.xml'
          // Collect the JaCoCo execution results.
          if (mainBuild) {
            jacoco exclusionPattern: '**/org/webtide/**,**/org/cometd/benchmark/**,**/org/cometd/examples/**',
                    execPattern: '**/target/jacoco.exec',
                    classPattern: '**/target/classes',
                    sourcePattern: '**/src/main/java'
          }
        }
      }

      stage("Javadoc ${jettyVersion}") {
        timeout(time: 15, unit: 'MINUTES') {
          withMaven(maven: mvnName,
                  jdk: jdk,
                  publisherStrategy: 'EXPLICIT',
                  globalMavenSettingsConfig: settingsName,
                  mavenOpts: mvnOpts) {
            sh "mvn -V -B javadoc:javadoc -e"
          }
        }
      }
    }
  }
}
