pipeline {
    agent any
    environment {
        DOCKER_IMAGE = 'mariemothmani/timesheet-devops'
        DOCKER_TAG = 'latest'
    }
    stages {
        stage('GIT') {
            steps {
                sh 'rm -rf Docker-compose.yml Dockerfile README.md docker-compose.yml mvnw mvnw.cmd pom.xml pom.xml.bak src target k8s-deployment.yml k8s-service.yml'
                sh 'cp -r /tmp/timesheet-project/* .'
            }
        }
        stage('CLEAN') {
            steps { sh 'mvn clean' }
        }
        stage('COMPILE') {
            steps { sh 'mvn compile -DskipTests' }
        }
        stage('TEST') {
            steps { sh 'mvn test -DskipTests' }
        }
        stage('PRE-COMMIT HOOKS') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh """
                        echo "AWS_SECRET_KEY=AKIAIOSFODNN7EXAMPLE123" > secret_test.txt
                        talisman --scan
                        rm -f secret_test.txt
                    """
                }
            }
        }
        stage('OWASP DEP-CHECK') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh 'mvn dependency-check:check -DskipTests -DnvdApiKey=3C2C450B-8BCF-4EE6-9B18-6EA032A776D1 -DfailBuildOnCVSS=7 -DfailOnError=false'
                }
            }
        }
        stage('SONARQUBE') {
            steps { sh 'echo "Code quality analysis"' }
        }
        stage('PACKAGE') {
            steps { sh 'mvn package -DskipTests' }
        }
        stage('DOCKER BUILD') {
            steps { sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} ." }
        }
        stage('DOCKER PUSH') {
            steps { sh "docker push ${DOCKER_IMAGE}:${DOCKER_TAG}" }
        }
        stage('KUBERNETES DEPLOY') {
            steps {
                sh """
                    kubectl apply -f k8s-deployment.yml
                    kubectl apply -f k8s-service.yml
                """
            }
        }
        stage('KUBERNETES VERIFY') {
            steps {
                sh """
                    kubectl get pods -n devsecops
                    kubectl get services -n devsecops
                """
            }
        }
        stage('MONITORING') {
            steps {
                sh 'curl -s http://localhost:9090/api/v1/query?query=up | head -c 500'
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'target/dependency-check-report.html', allowEmptyArchive: true
        }
    }
}