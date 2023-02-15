import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

import static com.lesfurets.jenkins.unit.global.lib.ProjectSource.projectSource
import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DockerPipelineTest extends BasePipelineTest {

    @Override
    @BeforeAll
    void setUp() throws Exception {
        scriptRoots += 'vars'
        super.setUp()
        helper.registerAllowedMethod('pipeline', [Closure.class], null)
        helper.registerAllowedMethod('options', [Closure.class], null)
        helper.registerAllowedMethod('timeout', [Map.class], null)
        helper.registerAllowedMethod('triggers', [Map.class], null)
        helper.registerAllowedMethod('timestamps', [], null)
        helper.registerAllowedMethod('agent', [Closure.class], null)
        helper.registerAllowedMethod('stages', [Closure.class], null)
        helper.registerAllowedMethod('steps', [Closure.class], null)
        helper.registerAllowedMethod('script', [Closure.class], null)
        helper.registerAllowedMethod('readMavenPom', [Map.class], null)
        //todo mocking this function doesn't work
        helper.registerAllowedMethod('getJIRAPass', []) { args -> return "login:pass" }
        //todo "when" doesn't work
        helper.registerAllowedMethod('when', []) { args -> return true }
        binding.setVariable('none', {})
        binding.setVariable('any', {})
        binding.setVariable('WORKSPACE', "WORKSPACE")
        binding.setVariable('BUILD_URL', "BUILD_URL")
        binding.setVariable('NO_PROXY', "NO_PROXY")
        binding.setVariable('HTTP_PROXY', "HTTP_PROXY")
        binding.setVariable('HTTPS_PROXY', "HTTPS_PROXY")
        binding.setVariable('BUILD_NUMBER', "BUILD_NUMBER")
        String clonePath = '/tmp'

        def library = library()
                .name('generic-nodejs-script-pipeline')
                .retriever(projectSource())
                .defaultVersion('<notNeeded>')
                .targetPath('<notNeeded>')
                .allowOverride(true)
                .implicit(false)
                .build()
        helper.registerSharedLibrary(library)
    }

    @Test
    void test() {
        runScript("job/library/JenkinsfileDockerPipeline")
        printCallStack()
    }
}
