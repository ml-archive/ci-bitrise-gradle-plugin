import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.ApplicationVariant
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.process.ExecSpec

class BitriseContinuousIntegrationPlugin implements Plugin<Project> {
    private final String FORMAT_TASK_NAME = "ci%s" //Format for our task names (%s being the name of the task)
    private final String FORMAT_VALIDATE_TASK_NAME = "ci%sValidate"
    private final String FORMAT_ASSEMBLE_TASK_NAME = "assemble%s"
    private final String FORMAT_DEPLOY_TASK_NAME = "ci%sDeploy"
    private static String GROUP_NAME = "ci"
    //Format for our task names (%s being the name of the task)
    Project project


    @Override
    void apply(Project project) {
        this.project = project

        // Check if our project has the plugins we need

        def hasApp = project.plugins.withType(AppPlugin)

        def hasLib = project.plugins.withType(LibraryPlugin)

        if (!hasApp && !hasLib) {
            throw new IllegalStateException("CI GRADLE PLUGIN: 'android' or 'android-library' plugin required.")
        }

        // Add the extension object

        project.extensions.create("bitrise", BitriseExtension)

        //Go through each flavor and add extensions

        project.android.productFlavors.all { flavor ->
            flavor.ext.set(HockeyValidator.HOCKEY_ID_TYPE_RELEASE, null)
            flavor.ext.set(HockeyValidator.HOCKEY_ID_TYPE_STAGING, null)
            flavor.ext.set("deploy", "")
        }

        //Task to generate our tasks

        project.task("generateCITasks") {
            generateFlavorTasks(project)
        } setGroup(GROUP_NAME)

        //Create our task to run all commands

        project.task("!ciDeployAll") dependsOn {
            List<String> taskNames = getTaskNames()

            project.tasks.findAll {
                task -> PluginUtils.arrayContainsString(taskNames, task.name)
            }
        } setGroup(GROUP_NAME)


        project.task("ciDeployFilters") dependsOn {
            List<String> taskNames = getFilteredFlavorTaskNames()

            project.tasks.findAll {
                task -> PluginUtils.arrayContainsString(taskNames, task.name)
            }
        } setGroup(GROUP_NAME)

    }

    List<String> getFilteredFlavorTaskNames() {
        String flavorNames = project.bitrise.flavorFilter

        List<String> taskNames = new ArrayList<>()

        String[] filters = flavorNames.tokenize("|")

        for (ApplicationVariant variant : project.android.applicationVariants) {
            String variantName = PluginUtils.capFirstLetter(variant.name)

            String[] deploymentModes = PluginUtils.getDeploymentModes(project, variant)

            boolean shouldCreateTask = PluginUtils.arrayContainsString(deploymentModes, variant.name)

            boolean isInFilter = PluginUtils.stringContainsArray(variant.name,filters)

            String taskName = String.format(FORMAT_TASK_NAME, variantName)

            if (shouldCreateTask && isInFilter) {
                taskNames.add(taskName)
            }
        }

        return taskNames
    }


    List<String> getTaskNames() {
        List<String> taskNames = new ArrayList<>()

        for (ApplicationVariant variant : project.android.applicationVariants) {
            String variantName = PluginUtils.capFirstLetter(variant.name)

            String[] deploymentModes = PluginUtils.getDeploymentModes(project, variant)

            boolean shouldCreateTask = PluginUtils.arrayContainsString(deploymentModes, variant.name)

            String taskName = String.format(FORMAT_TASK_NAME, variantName)

            if (shouldCreateTask) {
                taskNames.add(taskName)
            }
        }

        return taskNames
    }

    void generateFlavorTasks(Project project) {
        //Generate the children tasks

        project.android.applicationVariants.all { variant ->

            assert variant instanceof ApplicationVariant

            String variantName = PluginUtils.capFirstLetter(variant.name)

            String tnAssemble = String.format(FORMAT_ASSEMBLE_TASK_NAME, variantName)
            String tnValidate = String.format(FORMAT_VALIDATE_TASK_NAME, variantName)
            String tnDeploy = String.format(FORMAT_DEPLOY_TASK_NAME, variantName)
            String tnParent = String.format(FORMAT_TASK_NAME, variantName)

            String[] deploymentModes = PluginUtils.getDeploymentModes(project, variant)

            boolean shouldCreateTask = PluginUtils.arrayContainsString(deploymentModes, variant.name)

            if (shouldCreateTask) {
                Task taskAssemble = project.tasks.findByName(tnAssemble)

                //Create our validation Task (use to make sure our hockey ID is present)
                Task taskValidate = project.task(tnValidate)

                taskValidate.doLast {
                    println()
                    println "Validating Hockey ID"
                    println()
                    println "Validated Hockey ID: " + HockeyValidator.validate(variant)
                    println()
                }

                taskValidate.setGroup(GROUP_NAME)

                //Create our deployment task

                Task taskDeploy = project.task(tnDeploy)

                taskDeploy.doLast {
                    singleDeploy(project, variant)
                }

                taskDeploy.setGroup(GROUP_NAME)

                //Setup task order

                taskAssemble.shouldRunAfter taskValidate

                taskDeploy.shouldRunAfter taskAssemble

                //Create our parent task (Will run all the tasks above)
                Task taskParent = project.task(tnParent)

                taskParent.doLast {
                    println()
                    println "Bitrise CI: " + variantName
                    println()
                } dependsOn {
                    List<Task> tasks = [taskValidate, taskAssemble, taskDeploy]
                    return tasks.asCollection()
                }

                taskParent.setGroup(GROUP_NAME)

            }
        }

        //Generate the parent tasks

        project.android.productFlavors.all { flavor ->
            String flavorName = PluginUtils.capFirstLetter(flavor.name)

            String taskName = String.format(FORMAT_TASK_NAME, flavorName)

            Task taskParent = project.task(taskName)

            taskParent.dependsOn {
                //The following tasks should run before this task
                project.tasks.findAll {
                    task ->
                        if (task.name.contains(GROUP_NAME) &&
                                task.name.contains(flavorName) &&
                                !task.name.equalsIgnoreCase(taskName)) {

                            println task.name

                            return task
                        }
                }
            }

            taskParent.setGroup(GROUP_NAME)
        }
    }

    void saveFile(String fileName, JsonElement jsonElement) {
        fileName = String.format("%sHockeyBuild.json", fileName);

        def projectPath = project.buildDir.toString();

        def hockeyFilePath = new File(projectPath, fileName).toString()

        def stringsFile = new File(hockeyFilePath)

        println "HockeySDK JSON output file:"

        println jsonElement

        stringsFile.text = jsonElement
    }


    JsonObject singleDeploy(Project project, ApplicationVariant variant) {
        String apk = null
        String hockeyId
        String appId = variant.applicationId
        String mappingFile = variant.mappingFile

        String[] deploymentModes = PluginUtils.getDeploymentModes(project, variant)

        boolean shouldDeploy = PluginUtils.arrayContainsString(deploymentModes, variant.name)

        //If we aren't deploying then just abort
        if (!shouldDeploy) {
            return
        }

        //Pull whatever value we need
        hockeyId = HockeyValidator.validate(variant)

        variant.outputs.each { output ->
            apk = output.outputFile
        }

        println()
        println('--------------------------------------------------')
        println('apk: ' + apk)
        println('hockeyId: ' + hockeyId)
        println('appId: ' + appId)
        println('mappingFile: ' + mappingFile)
        println('--------------------------------------------------')
        println()

        //This is a comment

        JsonObject jsonObject = new JsonObject()

        jsonObject.addProperty("build", apk)
        jsonObject.addProperty("hockeyId", hockeyId)
        jsonObject.addProperty("appId", appId)
        jsonObject.addProperty("mappingFile", mappingFile.toString())
        jsonObject.addProperty("deploy", Arrays.toString(deploymentModes))

        project.exec(new Action<ExecSpec>() {
            @Override
            void execute(ExecSpec execSpec) {
                execSpec.commandLine 'envman', 'init'
                execSpec.commandLine 'envman', 'add', '--key', 'HOCKEYBUILDSJSON', '--value', jsonObject.toString()
                println "Wrote json to ENV VAR"
            }
        })

        return jsonObject
    }
}
