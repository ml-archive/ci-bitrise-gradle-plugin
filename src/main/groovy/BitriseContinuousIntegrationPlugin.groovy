import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.ApplicationVariant
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

class BitriseContinuousIntegrationPlugin implements Plugin<Project> {
    public static final String FORMAT_TASK_NAME = "ci%s"
    //Format for our task names (%s being the name of the task)
    public static final String FORMAT_VALIDATE_TASK_NAME = "ci%sValidate"
    public static final String FORMAT_ASSEMBLE_TASK_NAME = "assemble%s"
    public static final String FORMAT_DEPLOY_TASK_NAME = "ci%sDeploy"
    public static final String GROUP_NAME = "ci"
    private static final String BUILD_DIR_ENV = "BITRISE_SOURCE_DIR"
    private static String BUILD_DIR = ""
    private static JsonArray jsonArray = new JsonArray()
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

        //Generate Build Dir
        generateBuildDir()

        // Add the extension object
        project.extensions.create("bitrise", BitriseExtension)

        //Go through each flavor and add extensions
        project.android.productFlavors.all {
            flavor ->
                flavor.ext.set("hockeyAppId", [:])
                flavor.ext.set("deploy", "")
        }

        //Task to generate our tasks
        project.task("generateCITasks") {
            generateFlavorTasks()
        } setGroup(GROUP_NAME)

        //Create our task to run all commands
        createDeployAllTask()
        //Create our task to deploy only selected flavors
        createDeployFiltersTask()


        if (this.project.bitrise.envManEnabled) {
            project.task("ciDebug") doLast {
                //Try to write our file to Envman
                try {
                    project.exec {
                        workingDir BUILD_DIR
                        commandLine 'envman', 'print'
                    }
                } catch (Exception exception) {
                    //println "Error unable to locate envman skipping step"
                    //If we get an exception here its most likely because we don't have envman installed
                }
            } setGroup(GROUP_NAME)
        }

        project.afterEvaluate {
            project.android.applicationVariants.all { variant ->
                generateHockey(variant);
            }
        }
    }

    void initializeEnvman() {
        try {
            project.exec {
                workingDir BUILD_DIR
                commandLine 'envman', 'init'
            }
        } catch (Exception exception) {
        }
    }

    List<Task> getTasks(boolean filtered) {
        List<String> taskNames = filtered ? PluginUtils.getFilteredTaskNames(project) : PluginUtils.getTaskNames(project)
        List<Task> tasks = new ArrayList<>()
        for (String taskName : taskNames) {
            Task task = project.tasks.findByName(taskName)
            tasks.add(task)
        }
        return tasks
    }

    void createDeployAllTask() {
        project.task("ciDeployAll") doLast {
            jsonArray = new JsonArray()
        } dependsOn {
            return getTasks(false)
        } setGroup(GROUP_NAME)
    }

    void createDeployFiltersTask() {
        project.task("ciDeployFilters") doLast {
            jsonArray = new JsonArray()
        } dependsOn {
            return getTasks(true)
        } setGroup(GROUP_NAME)
    }

    void generateFlavorTasks() {
        //Generate the children tasks

        project.android.applicationVariants.all {
            variant ->
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
                        // < This function validates the ID
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

        project.android.productFlavors.all {
            flavor ->
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

    JsonObject singleDeploy(Project project, ApplicationVariant variant) {
        String apk = null
        String hockeyId
        String appId = variant.applicationId
        String mappingFile = variant.mappingFile

        String[] deploymentModes = PluginUtils.getDeploymentModes(project, variant)

        boolean shouldDeploy = PluginUtils.arrayContainsString(deploymentModes, variant.name)
        String deployMode = PluginUtils.searchArray(deploymentModes, variant.name)

        //If we aren't deploying then just abort
        if (!shouldDeploy) {
            return
        }

        //Pull whatever value we need
        hockeyId = HockeyValidator.validate(variant)

        variant.outputs.each {
            output ->
                apk = output.outputFile
        }

        println()
        println('')
        println('apk: ' + apk)
        println('hockeyId: ' + hockeyId)
        println('appId: ' + appId)
        println('mappingFile: ' + mappingFile)
        println('')
        println()

        JsonObject jsonObject = new JsonObject()

        jsonObject.addProperty("build", apk)
        jsonObject.addProperty("hockeyId", hockeyId)
        jsonObject.addProperty("appId", appId)
        jsonObject.addProperty("mappingFile", mappingFile.toString())
        jsonObject.addProperty("deploy", deployMode)

        jsonArray.add(jsonObject)

        saveFile(jsonArray)
        //Try to write our file to Envman
        if (this.project.bitrise.envManEnabled) {
            try {
                project.exec {
                    workingDir BUILD_DIR
                    commandLine 'envman', 'add', '--key', 'HOCKEYBUILDSJSON', '--value', jsonArray.toString()
                    println "Wrote json to ENV VAR"
                }
            } catch (Exception exception) {
                println "Error unable to locate envman skipping step"
            }
        }
        return jsonObject
    }

    void generateBuildDir() {
        String projectPath = System.getenv(BUILD_DIR_ENV)
        if (projectPath == null || projectPath.isEmpty()) {
            projectPath = project.rootDir.toString()
        }
        if (projectPath == null || projectPath.isEmpty()) {
            File bitriseSrc = new File("/bitrise/src/")
            if (bitriseSrc.exists()) {
                projectPath = bitriseSrc.toString()
            }
        }
        BUILD_DIR = projectPath
    }

    void saveFile(JsonArray array) {
        String fileName = "hockeybuilds.json"
        def hockeyFilePath = new File(BUILD_DIR, fileName).toString()
        println "hockeyFilePath: " + hockeyFilePath
        def stringsFile = new File(hockeyFilePath)

        if (!stringsFile.exists()) stringsFile.createNewFile()

        stringsFile.text = array
    }


    void generateHockey(ApplicationVariant variant) {
        //HockeyApp Injection
        if (variant.productFlavors.size() > 1) {
            //apps with dimensions like riide
            if (variant.productFlavors[0].ext.hockeyAppId instanceof Map && variant.productFlavors[0].ext.hockeyAppId[variant.productFlavors[1].name] != null) {
                //When environment keys specified
                variant.buildConfigField "String", "HOCKEYAPP_ID", "\"" + variant.productFlavors[0].ext.hockeyAppId[variant.productFlavors[1].name] + "\"";
                variant.resValue "string", "hockeyapp_id", variant.productFlavors[0].ext.hockeyAppId[variant.productFlavors[1].name]
            } else if (variant.productFlavors[1].ext.hockeyAppId instanceof Map && variant.productFlavors[1].ext.hockeyAppId[variant.productFlavors[0].name] != null) {
                //When environment keys specified
                variant.buildConfigField "String", "HOCKEYAPP_ID", "\"" + variant.productFlavors[1].ext.hockeyAppId[variant.productFlavors[0].name] + "\"";
                variant.resValue "string", "hockeyapp_id", variant.productFlavors[1].ext.hockeyAppId[variant.productFlavors[0].name]
            } else if (variant.productFlavors[0].ext.hockeyAppId instanceof String) {
                //When only an unique key has been specified
                variant.buildConfigField "String", "HOCKEYAPP_ID", "\"" + variant.productFlavors[0].ext.hockeyAppId + "\"";
                variant.resValue "string", "hockeyapp_id", variant.productFlavors[0].ext.hockeyAppId
            } else if (variant.productFlavors[1].ext.hockeyAppId instanceof String) {
                //When only an unique key has been specified
                variant.buildConfigField "String", "HOCKEYAPP_ID", "\"" + variant.productFlavors[1].ext.hockeyAppId + "\"";
                variant.resValue "string", "hockeyapp_id", variant.productFlavors[1].ext.hockeyAppId
            }
        } else if (variant.productFlavors[0].ext.hockeyAppId instanceof String) {
            //normal apps
            variant.buildConfigField "String", "HOCKEYAPP_ID", "\"" + variant.productFlavors[0].ext.hockeyAppId + "\"";
            variant.resValue "string", "hockeyapp_id", variant.productFlavors[0].ext.hockeyAppId
        }
    }
}
