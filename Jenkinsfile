pipeline {
  agent any

  options {
    disableConcurrentBuilds()
  }

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

    NVD_API_KEY = credentials('nvd-api-key')
    MAIL_TO     = "mariem.othmani@esprit.tn"
  }

  stages {

    stage('GIT') {
      steps {
        cleanWs()
        checkout([$class: 'GitSCM',
          branches: [[name: "*/${GIT_BRANCH}"]],
          extensions: [[$class: 'CloneOption', shallow: true, depth: 1, noTags: true, honorRefspec: true, timeout: 30]],
          userRemoteConfigs: [[url: "${GIT_URL}", credentialsId: "${GIT_CRED}",
                               refspec: "+refs/heads/${GIT_BRANCH}:refs/remotes/origin/${GIT_BRANCH}"]]
        ])
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
            git add secret_test.txt
            set +e
            /usr/local/bin/talisman --githook pre-commit > talisman-output.txt 2>&1
            RC=$?
            set -e
            cat talisman-output.txt
            {
              echo '<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8"><title>Rapport Talisman</title></head><body>'
              echo "<h1>Rapport Talisman (pre-commit)</h1>"
              echo "<p>Build : ${JOB_NAME} #${BUILD_NUMBER} - $(date '+%d/%m/%Y %H:%M')</p>"
              if [ $RC -ne 0 ]; then
                echo "<p><b>Résultat : secrets détectés, commit bloqué (code $RC)</b></p>"
              else
                echo "<p><b>Résultat : aucun secret détecté</b></p>"
              fi
              echo "<pre>"
              sed 's/&/\\&amp;/g; s/</\\&lt;/g; s/>/\\&gt;/g' talisman-output.txt
              echo "</pre></body></html>"
            } > talisman-report.html
            exit $RC
          '''
        }
      }
      post {
        always {
          archiveArtifacts artifacts: 'talisman-report.html', allowEmptyArchive: true
          sh 'git reset -q secret_test.txt || true; rm -f secret_test.txt talisman-output.txt'
        }
      }
    }

    stage('OWASP DEP-CHECK (SCA)') {
      steps {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
          sh 'mvn org.owasp:dependency-check-maven:12.1.0:check -DnvdApiKey=$NVD_API_KEY -DfailBuildOnCVSS=7 -DfailOnError=false -DossindexAnalyzerEnabled=false'
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

    stage('SECURITY ACCEPTANCE (Gauntlt)') {
      steps {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
          sh '''
            docker run --rm --network host \
              -v "$WORKSPACE/gauntlt":/work -w /work \
              --entrypoint bash gauntlt/gauntlt \
              -lc "gauntlt nmap.attack --format html > gauntlt-report.html 2>/dev/null || true; gauntlt nmap.attack"
          '''
        }
      }
      post {
        always {
          archiveArtifacts artifacts: 'gauntlt/gauntlt-report.html', allowEmptyArchive: true
        }
      }
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
        catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
          withCredentials([file(credentialsId: "${KUBECONFIG_CRED}", variable: 'KCFG')]) {
            sh '''
              export KUBECONFIG="$KCFG"
              ${KUBECTL} -n ${K8S_NAMESPACE} set image deployment/${K8S_DEPLOYMENT} ${K8S_CONTAINER}=${IMAGE_NAME}:${IMAGE_TAG}
              ${KUBECTL} -n ${K8S_NAMESPACE} rollout status deployment/${K8S_DEPLOYMENT} --timeout=180s
            '''
          }
        }
      }
    }

    stage('KUBERNETES VERIFY') {
      steps {
        catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
          withCredentials([file(credentialsId: "${KUBECONFIG_CRED}", variable: 'KCFG')]) {
            sh '''
              export KUBECONFIG="$KCFG"
              ${KUBECTL} -n ${K8S_NAMESPACE} get pods -o wide
              ${KUBECTL} -n ${K8S_NAMESPACE} get svc
            '''
          }
        }
      }
    }

    stage('SECURITY SMOKE TEST (ZAP Baseline)') {
      steps {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
          timeout(time: 10, unit: 'MINUTES') {
            withCredentials([file(credentialsId: "${KUBECONFIG_CRED}", variable: 'KCFG')]) {
              sh '''
                export KUBECONFIG="$KCFG"
                docker rm -f zap-scan > /dev/null 2>&1 || true

                # 1) Exposer l'application deployee sur Kubernetes (port libre 18082)
                ${KUBECTL} -n ${K8S_NAMESPACE} port-forward deployment/${K8S_DEPLOYMENT} 18082:8082 > /tmp/pf.log 2>&1 &
                PF_PID=$!
                sleep 10

                # 2) Verifier qu'elle repond
                curl -s -o /dev/null -w "Application : HTTP %{http_code}\\n" http://localhost:18082/timesheet-devops/user/retrieve-all-users || true

                # 3) Scan ZAP Baseline (passif, borne en temps)
                mkdir -p "$WORKSPACE/zap"
                chmod 777 "$WORKSPACE/zap"
                docker run --rm --name zap-scan --network host -v "$WORKSPACE/zap":/zap/wrk:rw \
                  ghcr.io/zaproxy/zaproxy:stable \
                  zap-baseline.py -t http://localhost:18082 -r zap-report.html -I -m 1 -T 3 || true

                # 4) Fermer le port-forward
                kill $PF_PID || true
              '''
            }
          }
        }
      }
      post {
        always {
          sh 'docker rm -f zap-scan > /dev/null 2>&1 || true'
          archiveArtifacts artifacts: 'zap/zap-report.html', allowEmptyArchive: true
        }
      }
    }

    stage('MONITORING') {
      steps {
        sh '''
          echo "=== Verification Prometheus : metrique up des cibles ==="
          curl -s --max-time 10 "http://localhost:9090/api/v1/query?query=up" || true
          echo ""
          echo "Prometheus : http://localhost:9090"
          echo "Grafana    : http://localhost:3000"
        '''
      }
    }

    stage('OPENSCAP SCAN (Continuous Scanning)') {
      steps {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
          sh '''
            CACHE=/var/lib/jenkins/openscap-cache
            OVAL="$CACHE/com.ubuntu.jammy.usn.oval.xml"
            mkdir -p "$CACHE" "$WORKSPACE/openscap"
            if [ ! -f "$OVAL" ]; then
              wget -q -c --tries=5 --timeout=60 -O "$CACHE/oval.xml.bz2" https://security-metadata.canonical.com/oval/com.ubuntu.jammy.usn.oval.xml.bz2
              bunzip2 -f "$CACHE/oval.xml.bz2"
              mv "$CACHE/oval.xml" "$OVAL"
            fi
            cd "$WORKSPACE/openscap"
            oscap oval eval --report openscap-report.html "$OVAL" > openscap-results.txt
            echo "Définitions vulnérables : $(grep -c ': true' openscap-results.txt)"
            test -s openscap-report.html
          '''
        }
      }
      post {
        always {
          archiveArtifacts artifacts: 'openscap/openscap-report.html', allowEmptyArchive: true
        }
      }
    }
  }

  post {
    always {
      sh 'docker image prune -f || true'
    }
    success {
      script {
        try {
          mail to: "${MAIL_TO}",
               subject: "BUILD REUSSI : ${env.JOB_NAME} #${env.BUILD_NUMBER}",
               body: "Le pipeline DevSecOps s'est termine avec succes.\n\nJob : ${env.JOB_NAME}\nBuild : #${env.BUILD_NUMBER}\nStatut : SUCCESS\nDetails : ${env.BUILD_URL}"
        } catch (e) {
          echo "Envoi du mail impossible : ${e.message}"
        }
      }
    }
    unstable {
      script {
        try {
          mail to: "${MAIL_TO}",
               subject: "BUILD TERMINE (alertes securite) : ${env.JOB_NAME} #${env.BUILD_NUMBER}",
               body: "Le pipeline s'est execute en entier. Scans securite (Talisman, OWASP, Gauntlt, ZAP, OpenSCAP) : voir les rapports dans les artefacts.\n\nJob : ${env.JOB_NAME}\nBuild : #${env.BUILD_NUMBER}\nStatut : UNSTABLE\nDetails : ${env.BUILD_URL}"
        } catch (e) {
          echo "Envoi du mail impossible : ${e.message}"
        }
      }
    }
    failure {
      script {
        try {
          mail to: "${MAIL_TO}",
               subject: "BUILD ECHOUE : ${env.JOB_NAME} #${env.BUILD_NUMBER}",
               body: "Le pipeline a echoue.\n\nJob : ${env.JOB_NAME}\nBuild : #${env.BUILD_NUMBER}\nDetails : ${env.BUILD_URL}"
        } catch (e) {
          echo "Envoi du mail impossible : ${e.message}"
        }
      }
    }
  }
}

