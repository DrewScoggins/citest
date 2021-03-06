// Import the utility functionality.
import jobs.generation.*;

def project = GithubProject
def branch = GithubBranchName
def gitUrl = Utilities.calculateGitURL(project)

// Define the nightly workflows that measure the stability of performance machines
// Put these in a folder

def stabilityTestingFolderName = 'stability_testing'
folder('stability_testing') {}

// Defines stability testing for all linux OS's.  It might be possible to unify this into all unixes.
// Can't use pipeline here.  The postbuild step that is used to launch the subsequent builds doesn't work
// with the pipeline project type.  It might be possible to do this other ways.  For instance, evaluating all
// all eligible nodes based on an expression in a pipeline job and launching downstream jobs.

// We run two types:
// 1) Native testing of known binaries (a few parsec benchmarks) that are very stable.
// 2) Managed testing of known binaries that are stable

['unix', 'windows'].each { osFamily ->
    def nativeStabilityJob = job(Utilities.getFullJobName("${osFamily}_native_stability_test", false, stabilityTestingFolderName)) {
        // Add a parameter to specify which nodes to run the stability job across
        parameters {
            labelParam('NODES_LABEL') {
                allNodes('allCases', 'AllNodeEligibility')
                defaultValue("${osFamily} && performance")
                description('Nodes label expression to run the unix stability job across')
            }
        }
        steps {
            if (osFamily == 'windows') {
                batchFile('runPythonOnWindows.bat')
            }
            else {
                shell("stability/linux_native-stability-test.py")
            }
        }
    }

    // Standard job setup, etc.
    Utilities.standardJobSetup(nativeStabilityJob, project, false, "*/${branch}")

    job('take_machine_offline_upon_native_failure') {
        publishers {
            flexiblePublish {
                conditionalAction {
                    condition {
                        status('ABORTED', 'FAILURE')
                    }
                    steps {
                        systemGroovyScriptFile('jobs/scripts/take_offline.groovy')
                    }
                    publishers {
                        extendedEmail {
                            recipientList('xiwe@microsoft.com')
                            defaultSubject('Stability test fails and the machine is taken offline')
                            contentType('text/html')
                        }
                    }
                }
            }
        }
    }
    
    // Set the cron job here.  We run nightly on each flavor, regardless of code changes
    Utilities.addPeriodicTrigger(nativeStabilityJob, "@daily", true /*always run*/)
    
    // Managed stability testing

    def managedStabilityJob = job(Utilities.getFullJobName("${osFamily}_managed_stability_test", false, stabilityTestingFolderName)) {
        // Add a parameter to specify which nodes to run the stability job across
        parameters {
            labelParam('NODES_LABEL') {
                allNodes('allCases', 'AllNodeEligibility')
                defaultValue("${osFamily} && performance")
                description('Nodes label expression to run the unix stability job across')
            }
        }
        steps {
            if (osFamily == 'windows') {
                batchFile('runPythonOnWindows.bat')
            }
            else {
                shell("stability/linux_native-stability-test.py")
            }
        }
    }

    // Standard job setup, etc.
    Utilities.standardJobSetup(managedStabilityJob, project, false, "*/${branch}")

    job('take_machine_offline_upon_managed_failure') {
        publishers {
            flexiblePublish {
                conditionalAction {
                    condition {
                        status('ABORTED', 'FAILURE')
                    }
                    steps {
                        systemGroovyScriptFile('jobs/scripts/take_offline.groovy')
                    }
                    publishers {
                        extendedEmail {
                            recipientList('xiwe@microsoft.com')
                            defaultSubject('Stability test fails and the machine is taken offline')
                            contentType('text/html')
                        }
                    }
                }
            }
        }
    }
}

// Create a perf job for roslyn testing

def roslynPerfJob = pipelineJob('rosly_perf_proto') {
    definition {
        cpsScm {
            scm {
                // Read the script from source control at execution time
                git(gitUrl)
                // Load it from the appropriate location
                scriptPath('roslyn/roslyn-perf-proto.groovy')
            }
        }
    }
}

// Standard job setup, etc.
Utilities.standardJobSetup(roslynPerfJob, project, false, "*/${branch}")