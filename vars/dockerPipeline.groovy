
def call(Map config) {
    def jiraAuthSSM = config.getOrDefault('jiraAuthSSM', "/Wiley/DO-ContentTech/app-secrets/shared/Jira/")
    def confluenceAuthSSM = config.getOrDefault('confluenceAuthSSM', "/Wiley/DO-ContentTech/app-secrets/shared/confluence/")
    def dockerfilePath = config.getOrDefault('dockerfilePath', 'vars/Dockerfile')
    def scriptName = config.getOrDefault('scriptName', null)
    def ignoreBranch = config.getOrDefault('ignoreBranch', false)
    def dockerRunParams = config.getOrDefault('dockerRunParams', '')
    def dockerBuildParams = config.getOrDefault('dockerBuildParams', '')
    def jenkinsInputParameters = config.getOrDefault('jenkinsInputParameters', '')
    def jenkinsPreDefinedParameters = config.getOrDefault('jenkinsPreDefinedParameters', '')
    def configName = config.getOrDefault('configName', '')
    def cronString = config.getOrDefault('cronString', '')
    def snowflakeJsonPath = config.getOrDefault('snowflakeJsonPath', '')
    def snowflakeS3Path = config.getOrDefault('snowflakeS3Path', '')
    def emailForJenkinsReport = config.getOrDefault('emailForJenkinsReport', 'vnabatov@wiley.com')
    def fileParameterPath = config.getOrDefault('fileParameterPath', '') // by default saves to workspace
    def secretsConfigPath = config.getOrDefault('secretsConfigPath', '')
    def useConfigJson = config.getOrDefault('useConfigJson', true)
    def dockerImageName = config.getOrDefault('dockerImageName', scriptName.toLowerCase())

    def secrets_app_name = "cmh-production-dashboard-api"
    def build_secrets_app_path = "build-secrets/" + secrets_app_name
    def build_secrets_global_path = "build-secrets/GLOBAL"
    def secrets_sys_name = "cmh"
    def tmp_secrets_dir_name = "tmp_secrets"
    def vault_addr = "https://ctgss-hcvault-nonprod.aws.wiley.com"

    def configFromUserInput = ""
    def configPreDefined = ""
    def additionalSecretsEnv = ""
    def additionalSecretsJS = ""
    def configNameStr = ''

    pipeline {
        //todo "agent" "triggers" "options" mock doesn't work

//        agent {
//            label 'ctci-jenkins-node-qa'
//        }
//        triggers { cron(cronString) }
//        options {
//            disableConcurrentBuilds()
//            ansiColor('xterm')
//        }
        stages {
            stage('Input and credentials') {
                steps {
                    script {
                        print "dockerPipeline cpp-helper-scripts"

                        if(secretsConfigPath) {
                            def additionalSecrets = getSecretsBySecretsConfig(
                                    secretsConfigPath,
                                    vault_addr,
                                    build_secrets_global_path,
                            )
                            additionalSecretsEnv = additionalSecrets[0]
                            additionalSecretsJS = additionalSecrets[1]
                        }

                        if(configName != '') {
                            configNameStr+="""\\"configName\\": \\"$configName\\","""
                        }

                        if(jenkinsPreDefinedParameters != ''){
                            jenkinsPreDefinedParameters.each { key, val ->
                                env["$key"] = val;
                                print "$key"
                                print env["$key"]
                                configPreDefined+="""\\"$key\\": \\"$val\\","""
                            }
                        }

                        if(jenkinsInputParameters != ''){
                            userInput = getInput(jenkinsInputParameters)
                            print "userInput"
                            print userInput
                            configFromUserInput+=parseUserInput(userInput, fileParameterPath);
                        }

                        if (useConfigJson) {
                            def confluenceCredentials = getSSMParameters(confluenceAuthSSM)

                            def dashboardSecrets = getBuildSecrets(
                                    vault_addr,
                                    build_secrets_global_path,
                                    build_secrets_app_path,
                                    tmp_secrets_dir_name,
                                    secrets_app_name,
                                    secrets_sys_name)

                            dashboardSecrets.each { key, val ->
                                print "$key"
                                configFromUserInput+="""\\"$key\\": \\"${val.replace('\r', 'RRRRR').replace('\n', 'NNNNN').replace('"', 'QQQQQ').replace('\\', 'SSSSS')}\\","""
                            }

                            def couchbase = getBuildSecrets(
                                    vault_addr,
                                    "build-secrets/GLOBAL",
                                    "build-secrets/cmh-production-api-couchbase",
                                    "tmp_secrets_dir_name-CouchbaseUser",
                                    "cmh-production-api-couchbase",
                                    "cmh")

                            def jiraPass
                            if(getJIRAPass()) {
                                jiraPass = getJIRAPass().split(":")[1]
                            }
                            sh """
                            {
                            echo "{\\
                                \\"host\\": \\"jira.wiley.com\\",\\
                                \\"username\\": \\"ct_user\\",\\
                                \\"password\\": \\"${jiraPass}\\",\\
                                \\"confluenceBasicAuthToken\\": \\"${confluenceCredentials.cpptoken}\\",\\
                                \\"loggingOnly\\": \\"true\\",\\
                                \\"buildJobURL\\": \\"$BUILD_URL\\",\\
                                \\"emailUser\\": \\"cpp_bot@wiley.com\\",\\
                                \\"emailPass\\": \\"\\",\\
                                \\"emailHost\\": \\"smtpgate.wiley.com\\",\\
                                \\"emailPort\\": 25,\\
                                $additionalSecretsJS
                                $configNameStr
                                $configFromUserInput
                                $configPreDefined
                                \\"couchbaseLoginAdmin\\": \\"${couchbase.login}\\",\\
                                \\"couchbasePasswordAdmin\\": \\"${couchbase.password}\\",\\
                                \\"emailSecure\\": false\\
                                }" > config/config.json
                            } 2> /dev/null
                            """
                        }
                    }
                }
            }
            stage('Run the Bot') {
//                when {
//                    expression { return scriptName && (env.BRANCH_NAME == "master" || ignoreBranch) }
//                }
                steps {
                    script {
                        def imageName = dockerImageName
                        def NO_PROXY_WITH_EXCLUSIONS = "thevault-wqa.aws.wiley.com," +
                                "cochraneqa-cms.wiley.com," +
                                "cochranesit-cms.wiley.com," +
                                "cochrane-cms.wiley.com," +
                                "es.amazonaws.com," +
                                "192.168.108.184," + //mysql51.tes-rus.net
                                "thevault.wiley.com," +
                                "vault-spa-ui-qa.aws.wiley.com,"+
                                "thevault-wqa.aws.wiley.com,"+
                                "thevault-uat.wiley.com,"+
                                "vault-app-batch-qa.aws.wiley.com,"+
                                "vault-app-batch-qa.aws.wiley.com,"+
                                "cochranedev.wiley.com,"+
                                "vault-dpp-qa.aws.wiley.com,"+
                                "vault-cms-bpa-qa.aws.wiley.com,"+
                                "vault-cms-beco-qa.aws.wiley.com,"+
                                "esbdevaus01.wiley.com," +
                                "esbsitaus01.wiley.com," +
                                "esbuataus01.wiley.com," +
                                "esbqaaus02.wiley.com," +
                                "esbuataus02.wiley.com," +
                                "car-lntbbwd-001.wiley.com," +
                                "esbppdaus01.wiley.com," +
                                "esbprodcar03.wiley.com," +
                                "thevault-wdev.aws.wiley.com,"+
                                "$NO_PROXY"

                        sh "docker build " +
                                "--build-arg HTTP_PROXY=${HTTP_PROXY} " +
                                "--build-arg HTTPS_PROXY=${HTTPS_PROXY} " +
                                "--build-arg NO_PROXY=${NO_PROXY_WITH_EXCLUSIONS} " +
                                "-t $dockerImageName " +
                                "--file $dockerfilePath $dockerBuildParams" +
                                "."

                        if(dockerRunParams) {
                            print("Docker run ${dockerImageName} with params ${dockerRunParams}")
                            sh """
                                set +x
                                docker run $dockerRunParams -v ${WORKSPACE}/src/scripts/:/src/scripts/ --env HTTP_PROXY=\"${HTTP_PROXY}\" --env HTTPS_PROXY=\"${HTTPS_PROXY}\" --env NO_PROXY=\"${NO_PROXY_WITH_EXCLUSIONS}\" $additionalSecretsEnv $dockerImageName 
                            """
                        } else {
                            if(scriptName.contains("/")){
                                sh "docker run -v ${WORKSPACE}/src/scripts/:/src/scripts/ "+
                                        "--env HTTP_PROXY=\"${HTTP_PROXY}\" " +
                                        "--env HTTPS_PROXY=\"${HTTPS_PROXY}\" " +
                                        "--env NO_PROXY=\"${NO_PROXY_WITH_EXCLUSIONS}\" " +
                                        "$dockerImageName node ./src/scripts/$scriptName"
                            } else {
                                sh "docker run -v ${WORKSPACE}/src/scripts/:/src/scripts/ "+
                                        "--env HTTP_PROXY=\"${HTTP_PROXY}\" " +
                                        "--env HTTPS_PROXY=\"${HTTPS_PROXY}\" " +
                                        "--env NO_PROXY=\"${NO_PROXY_WITH_EXCLUSIONS}\" " +
                                        "$dockerImageName node ./src/scripts/$scriptName/index.js"
                            }
                        }

                        removeSecrets()
                    }
                }
//                post {
//                    always {
//                        removeSecrets()
//                    }
//                    regression {
//                        emailext body: '$DEFAULT_CONTENT',
//                                recipientProviders: [culprits()],
//                                subject: '$DEFAULT_SUBJECT',
//                                to: emailForJenkinsReport
//                    }
//                }
            }

            stage('Put To S3') {
                //todo "when" doesn't work

//                when {
//                    expression { return scriptName && (env.BRANCH_NAME == "master" || ignoreBranch) && snowflakeS3Path && snowflakeJsonPath}
//                }
                steps {
                    dir('DO_WPA') {
                        checkout([$class                           : 'GitSCM',
                                  branches                         : [[name: "master"]],
                                  doGenerateSubmoduleConfigurations: false,
                                  extensions                       : [],
                                  submoduleCfg                     : [],
                                  userRemoteConfigs                : [[credentialsId: 'wmuser-ct', url: "git@github.com:/wiley/do-cmh"]]
                        ])
                    }


                    script {
                        def extention = snowflakeJsonPath.contains(".json") ? "json" : "csv"
                        def s3path = "s3://${snowflakeS3Path}-${BUILD_NUMBER}.${extention}"
                        sh """
                        set +x
                        source ./DO_WPA/misc/rollout/set_prod.sh
                        set -x
                        aws --region=us-east-1 s3 cp ${WORKSPACE}${snowflakeJsonPath} ${s3path}
                        mv ${WORKSPACE}${snowflakeJsonPath} ${WORKSPACE}${snowflakeJsonPath}_bak
                        """
                    }
                }
                //todo post doesn't work

//                post {
//                    always {
//                        removeSecrets()
//                    }
//                }
            }
        }
    }
}

def getSSMParameters(ssmSecretsPath) {
    if (ssmSecretsPath) {
        ssmParams = sh(
                script: "aws ssm get-parameters-by-path\
          --path $ssmSecretsPath\
          --region=us-east-1\
          --with-decryption\
          --query 'Parameters[].[Name,Value]'\
          --output text",
                returnStdout: true
        ).toString().trim()
        params = [:]
        ssmParams.split('\n').each { line ->
            param = line.split()
            if(param.length>1){
                key = param[0].split('/').last()
                value = param[1]
                params[key] = value
            }
        }

        return params
    }

    return null
}

def getInput(jenkinsInputParameters) {
    try {
        timeout(time: 5, unit: 'MINUTES') {
            res = input(
                    id: 'userInput', message: 'Select params',
                    parameters: jenkinsInputParameters
            )
        }
        return res
    } catch (err) {
        echo "User Input: ${err.toString()}"
        currentBuild.result = 'UNSTABLE'
        error('Exiting by timeout')
    }
}

def getBuildSecrets(
        vault_addr,
        build_secrets_global_path,
        build_secrets_app_path,
        secrets_dir_name,
        appName,
        sysName
) {
    sh( script: """
        mkdir -pm 700 $WORKSPACE/$secrets_dir_name
        set +x
        export VAULT_ADDR=$vault_addr
        vault login -no-print -method=aws role=$sysName-build-secrets-read
        arr_paths=()
        vault kv list build-secrets | grep -q "GLOBAL" && arr_paths+=($build_secrets_global_path)
        vault kv list build-secrets | grep -q $appName && arr_paths+=($build_secrets_app_path)
        echo "Secrets found in following paths: \${arr_paths[@]}"
        for path in \$(echo \${arr_paths[@]})
        do
            for key in \$(vault kv get -format=json \$path | jq -r '.data.data' | jq -r 'keys[]')
            do
                echo "\$(vault kv get -field=\$key \$path)" > "$WORKSPACE/$secrets_dir_name/\$key"
            done
        done
    """
    )

    def hParams = sh(
            script: "ls $WORKSPACE/$secrets_dir_name",
            returnStdout: true
    ).toString().trim()

    print("hParams="+hParams)

    if(hParams.length() > 0) {
        params = [:]
        hParams.split('\n').each { line ->
            key = line.trim()
            value = getSecret(key, secrets_dir_name)
            print key
            params[key] = value
        }
    }

    return params
}


def getSecret(key, secrets_dir_name) {
    sh 'set +x'
    return sh(
            script: "cat $WORKSPACE/$secrets_dir_name/$key",
            returnStdout: true
    ).toString().trim()
    sh 'set -x'
}

def removeSecrets() {
    sh """
set +x
rm -rf $WORKSPACE/tmp_secrets
set -x
"""
    sh """
set +x
rm -f $WORKSPACE/config/config.json
set -x
"""
    sh """
set +x
rm -rf $WORKSPACE/tmp_secrets*
set -x
    """
    return 1
}

def getJIRAPass() {
    try {
    withCredentials([usernameColonPassword(credentialsId: 'jira_ct', variable: 'userPassword')]) {
        return userPassword
    }} catch(e){
        print e
    }
}


def isFileInput(inputKey) {
    return ['.txt', '.csv'].any { inputKey.contains(it) }
}

def saveFile(fileName, file, fileParameterPath){
    def filePath = "${env.WORKSPACE}${fileParameterPath}/${fileName}"
    sh """       
        rm -f ${filePath}       
    """
    writeFile(file: "${filePath}", text: file.readToString())
}

def parseUserInput(userInput, fileParameterPath) {
    def res = ""
    userInput.each { key, val ->
        if (!key) return;
        if (isFileInput(key)) {
            saveFile(key, val, fileParameterPath)
            return;
        }

        env["$key"] = val;
        print "$key"
        print env["$key"]
        res+="""\\"$key\\": \\"${val.replace('\r', 'RRRRR').replace('\n', 'NNNNN').replace('"', 'QQQQQ').replace('\\', 'SSSSS')}\\","""
    }
    return res;
}

def getSecretsBySecretsConfig(
        secretsConfigPath,
        vault_addr,
        build_secrets_global_path
) {
    String additionalSecretsEnv = ""
    String additionalSecretsJS = ""
    sh "cat ${secretsConfigPath}"

    String secretsConfigContents = sh(
            script: "cat ${secretsConfigPath}",
            returnStdout: true
    ).toString().trim()

    def secretsConfig = readJSON text: secretsConfigContents
    secretsConfig.secretConfigRecords.each{ secretConfigRecord ->
        print("secretConfigRecord.appName = " + secretConfigRecord.appName)

        print("getBuildSecrets customAppSecrets")
        def customAppSecrets = getBuildSecrets(
                vault_addr,
                build_secrets_global_path,
                "build-secrets/${secretConfigRecord.appName}",
                "tmp_secrets_dir_name-${secretConfigRecord.appName}",
                secretConfigRecord.appName,
                secretConfigRecord.sysName)

        print("save secrets to a file secretConfigRecord.secrets")
        secretConfigRecord.secrets.each{ secret ->
            try {
                print("adding secretConfigRecord to config.js secretName = " + secret.secretName)
                switch(secretConfigRecord.type) {
                    case "js":
                        additionalSecretsJS+="""\\"${secret.secretName}\\": \\"${customAppSecrets[secret.secretVal]}\\","""
                        break
                    case "env":
                        additionalSecretsEnv+="-e ${secret.secretName}=${customAppSecrets[secret.secretVal]} "
                        break
                    default:
                        print("Unknown secretConfigRecord.type")
                        break
                }
            } catch(e) {
                print(e)
            }
        }
    }
    return [additionalSecretsEnv, additionalSecretsJS]
}
