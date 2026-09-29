pipeline {
  agent any

  environment {
    M2_HOME   = "/usr/share/maven"
    JAVA_HOME = "/usr/lib/jvm/java-17-openjdk-amd64"
    PATH      = "${M2_HOME}/bin:${env.PATH}"

    GIT_URL    = "https://github.com/mariemothmani12/timesheet-project.git"
    GIT_BRANCH = "devsecops"
    GIT_CRED   = "github-pat"

    SONAR_HOST_URL = "http://localhost:9000"
    SONAR_TOKEN    = credentials('sonar-token')

    IMAGE_NAME  = "mariemothmani/timesheet"
    IMAGE_TAG   = "1.1"
    DOCKER_CRED = "docker-registry-creds"

    K8S_NAMESPACE   = "chap4"
    K8S_DEPLOYMENT  = "timesheet-dep"
    K8S_CONTAINER   = "timesheet"
    KUBECONFIG_CRED = "kubeconfig-k8s"
    KUBECTL         = "/usr/local/bin/kubectl"

    NVD_API_KEY = "3C2C450B-8BCF-4EE6-9B18-6EA032A776D1"
    MAIL_TO     = "mariem.othmani@esprit.tn"
  }

  stages {

    stage('GIT') {
      steps {
        cleanWs()
        git branch: "${GIT_BRANCH}", url: "${GIT_URL}", credentialsId: "${GIT_CRED}"
        sh 'git log -1 --oneline'
      }
    }

    stage('CLEAN & COMPILE') {
      steps { sh 'mvn clean compile' }
    }

    stage('JUNIT / MOCKITO TESTS') {
      steps { sh 'mvn test' }
      post {
        always { junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml' }
      }
    }

    stage('PRE-COMMIT HOOKS (Talisman)') {
      steps {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
          sh '''
            echo "AWS_SECRET_KEY=AKIAIOSFODNN7EXAMPLE123" > secret_test.txt
            talisman --scan
            rm -f secret_test.txt
          '''
        }
      }
    }

    stage('OWASP DEP-CHECK (SCA)') {
      steps {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
          sh 'mvn org.owasp:dependency-check-maven:12.1.0:check -DnvdApiKey=${NVD_API_KEY} -DfailBuildOnCVSS=7 -DfailOnError=false -DossindexAnalyzerEnabled=false'
        }
      }
      post {
        always { archiveArtifacts artifacts: 'target/dependency-check-report.html', allowEmptyArchive: true }
      }
    }

    stage('SONARQUBE ANALYSIS') {
      steps {
        sh '''
          mvn sonar:sonar \
            -Dsonar.projectKey=timesheet-devops \
            -Dsonar.projectName=timesheet-devops \
            -Dsonar.host.url=${SONAR_HOST_URL} \
            -Dsonar.login=${SONAR_TOKEN} \
            -Dsonar.java.binaries=target/classes
        '''
      }
    }

    stage('PACKAGE') {
      steps { sh 'mvn package -DskipTests -Ddependency-check.skip=true' }
    }

    stage('NEXUS DEPLOY') {
      steps { sh 'mvn deploy -DskipTests -Ddependency-check.skip=true' }
    }

    stage('DOCKER BUILD & PUSH') {
      steps {
        withCredentials([usernamePassword(credentialsId: "${DOCKER_CRED}",
          usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
          sh '''
            docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
            set +x
            echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
            set -x
            docker push ${IMAGE_NAME}:${IMAGE_TAG}
            docker logout
          '''
        }
      }
    }

    stage('KUBERNETES DEPLOY') {
      steps {
        withCredentials([file(credentialsId: "${KUBECONFIG_CRED}", variable: 'KCFG')]) {
          sh '''
            export KUBECONFIG="$KCFG"
            ${KUBECTL} -n ${K8S_NAMESPACE} set image deployment/${K8S_DEPLOYMENT} ${K8S_CONTAINER}=${IMAGE_NAME}:${IMAGE_TAG} --record=true
            ${KUBECTL} -n ${K8S_NAMESPACE} rollout status deployment/${K8S_DEPLOYMENT} --timeout=180s
          '''
        }
      }
    }

    stage('KUBERNETES VERIFY') {
      steps {
        withCredentials([file(credentialsId: "${KUBECONFIG_CRED}", variable: 'KCFG')]) {
          sh '''
            export KUBECONFIG="$KCFG"
            ${KUBECTL} -n ${K8S_NAMESPACE} get pods -o wide
            ${KUBECTL} -n ${K8S_NAMESPACE} get svc
          '''
        }
      }
    }

    stage('MONITORING') {
      steps {
        sh '''
          echo "=== Verification Prometheus : metrique up des cibles ==="
          curl -s "http://localhost:9090/api/v1/query?query=up"
          echo ""
          echo "Prometheus : http://localhost:9090"
          echo "Grafana    : http://localhost:3000"
        '''
      }
    }
  }

  post {
    always {
      sh 'docker image prune -f || true'
    }
    success {
      mail to: "${MAIL_TO}",
           subject: "BUILD REUSSI : ${env.JOB_NAME} #${env.BUILD_NUMBER}",
           body: "Le pipeline DevSecOps s'est termine avec succes.\n\nJob : ${env.JOB_NAME}\nBuild : #${env.BUILD_NUMBER}\nStatut : SUCCESS\nDetails : ${env.BUILD_URL}"
    }
    unstable {
      mail to: "${MAIL_TO}",
           subject: "BUILD TERMINE (alertes securite) : ${env.JOB_NAME} #${env.BUILD_NUMBER}",
           body: "Le pipeline s'est execute en entier. Les scans Talisman/OWASP ont detecte des problemes (stages rouges = normal en DevSecOps).\n\nJob : ${env.JOB_NAME}\nBuild : #${env.BUILD_NUMBER}\nStatut : UNSTABLE\nDetails : ${env.BUILD_URL}"
    }
    failure {
      mail to: "${MAIL_TO}",
           subject: "BUILD ECHOUE : ${env.JOB_NAME} #${env.BUILD_NUMBER}",
           body: "Le pipeline a echoue.\n\nJob : ${env.JOB_NAME}\nBuild : #${env.BUILD_NUMBER}\nDetails : ${env.BUILD_URL}"
    }
  }
}
