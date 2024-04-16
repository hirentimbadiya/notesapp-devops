def call(Map config = [:]){
    withSonarQubeEnv(config.installationName){
        bat """
        cd server
        sonar-scanner -Dsonar.projectKey=inotebook-backend -Dsonar.projectName=inotebook-backend -Dsonar.sources=.
        """
    }

    withCredentials([string(credentialsId: 'dockerhub', variable: 'dockerhub_password')]) {
        sh """#!/bin/bash
        cd server
        docker build -t ${config.imageName} .

        docker logout
        docker login -u hirentimbadiya -p ${dockerhub_password}

        docker push ${config.imageName}
        """
    }

     withKubeConfig(caCertificate: '', clusterName: 'gke_gcp-learning-417116_us-central1-c_main-cluster', contextName: '', credentialsId: 'k8s-cred', namespace: 'jenkins', restrictKubeConfigAccess: true, serverUrl: 'https://35.226.254.176') {
        def chartDir = "../helmChart-Server"
        def chart = libraryResource "helmChart-Server/Chart.yaml"
        def deployment = libraryResource "helmChart-Server/templates/deployment.yaml"
        def service = libraryResource "helmChart-Server/templates/service.yaml"
        

        writeFile file: "./helmChart-Server/Chart.yaml", text: chart
        writeFile file: "./helmChart-Server/templates/deployment.yaml", text: deployment
        writeFile file: "./helmChart-Server/templates/service.yaml", text: service

        sh """#!/bin/bash
        cd server
        DIGEST=\$(docker image inspect --format='{{index .RepoDigests 0}}' ${config.imageName} | awk -F@ '{print \$2}') 
        echo \$DIGEST

        helm upgrade --install --namespace jenkins ${config.releaseName} ${chartDir} --set image.digest=\$DIGEST --values values.yaml
        """
    }
}