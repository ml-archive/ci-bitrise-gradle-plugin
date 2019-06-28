import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.ApplicationVariant
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

class BitriseContinuousIntegrationPlugin implements Plugin<Project> {
    public static final String FORMAT_TASK_NAME = "ci%s" //Format for our task names (%s being the name of the task)
    public static final String FORMAT_VALIDATE_TASK_NAME = "ci%sValidate"
    public static final String FORMAT_ASSEMBLE_TASK_NAME = "assemble%s"
    public static final String FORMAT_DEPLOY_TASK_NAME = "ci%sDeploy"
    public static final String GROUP_NAME = "ci"
    public static final String DEFAULT_OWNER_NAME = "Casper-Rasmussen-Organization"
    public static final String APPCENTER_NAME = "appCenter"

    private static final String BUILD_DIR_ENV = "BITRISE_SOURCE_DIR"
    private static String BUILD_DIR = ""

    private static String RN_HOTFIX = "Hotfix"
    private static String RN_FEATURE = "Feature"
    private static String RC_HOTFIX = "#F44336"
    private static String RC_FEATURE = "#3F51B5"

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
        project.android.productFlavors.whenObjectAdded { flavor ->
            flavor.extensions.create("appCenter", AppCenterExtension)
        }
        project.android.productFlavors.all {
            flavor ->
                flavor.ext.set(HockeyValidator.HOCKEY_ID_TYPE_RELEASE, null)
                flavor.ext.set(HockeyValidator.HOCKEY_ID_TYPE_STAGING, null)
                flavor.ext.set(APPCENTER_NAME, "")
        }
        //Task to generate our tasks
        project.task("generateCITasks") {
            generateFlavorTasks()
        } setGroup(GROUP_NAME)

        //Create our task to run all commands
        createDeployAllTask()
        //Create our task to deploy only selected flavors
        createDeployFiltersTask()
        //Create branch naming tasks
        generateBranchTasks()

        if (this.project.bitrise.envManEnabled) {
            project.task("ciDebug") doLast {
                //Try to write our file to Envman
                try {
                    project.exec {
                        workingDir BUILD_DIR
                        commandLine 'envman', 'print'
                    }
                } catch (Exception ignored) {
                    //println "Error unable to locate envman skipping step"
                    //If we get an exception here its most likely because we don't have envman installed
                }
            } setGroup(GROUP_NAME)
        }

        //Apply the ribbon branch
        applyBranchRibbonizer()
    }

    /**
     * Returns an array of tasks which can either be all tasks of just the filtered tasks
     * @param filtered Should this task only returned the filtered list of tasks or all of them (true = all tasks)
     * @return List < Task >  Task List
     */
    List<Task> getTasks(boolean filtered) {
        List<String> taskNames = filtered ? PluginUtils.getFilteredTaskNames(project) : PluginUtils.getTaskNames(project)
        List<Task> tasks = new ArrayList<>()
        for (String taskName : taskNames) {
            Task task = project.tasks.findByName(taskName)
            tasks.add(task)
        }
        return tasks
    }

    /**
     *  Generates the parent task for generate all builds
     */
    void createDeployAllTask() {
        project.task("ciDeployAll") doLast {
            jsonArray = new JsonArray()
        } dependsOn {
            def tasks = getTasks(false)
            println(tasks.toString())
            return tasks
        } setGroup(GROUP_NAME)
    }

    /**
     *  Generates the parent task for generate all filtered builds
     */
    void createDeployFiltersTask() {
        project.task("ciDeployFilters") doLast {
            jsonArray = new JsonArray()
        } dependsOn {
            return getTasks(true)
        } setGroup(GROUP_NAME)
    }

    /**
     * This adds a {versionName}-{branchName} to each build so we know which build is generate from which branch
     * This most likely needs a better name
     * Note: Can be disabled via the branchMode variable
     */
    void generateBranchTasks() {
        if (this.project.bitrise.branchMode) {
            String branchName = PluginUtils.getBranchName(project)

            project.android.applicationVariants.all {
                variant ->
                    assert variant instanceof ApplicationVariant
                    println()
                    println("Branch Name: " + branchName)
                    println()

                    if (!branchName.contains("/")) {
                        return
                    }

                    variant.checkManifest.doLast {
                        String typeName = branchName.split("/")[1].toUpperCase()
                        String variantName = variant.getVersionName()
                        String newVersionName = String.format("%s-%s", variantName, typeName)
                        variant.outputs.all { output ->
                            output.versionNameOverride = newVersionName
                        }
                    }
            }
        } else {
            println("Branch mode disabled, skipping task!")
        }
    }
    /**
     * Generate the build task for each flavor
     */

    void generateFlavorTasks() {
        //Generate the children tasks

        project.android.applicationVariants.all {
            variant ->
                assert variant instanceof ApplicationVariant

                if (PluginUtils.shouldCreateTask("release", variant.name)) {
                    String variantName = PluginUtils.capFirstLetter(variant.name)

                    String tnAssemble = String.format(FORMAT_ASSEMBLE_TASK_NAME, variantName)
                    String tnValidate = String.format(FORMAT_VALIDATE_TASK_NAME, variantName)
                    String tnDeploy = String.format(FORMAT_DEPLOY_TASK_NAME, variantName)
                    String tnParent = String.format(FORMAT_TASK_NAME, variantName)

                    //Task to assemble the build
                    Task taskAssemble = project.tasks.findByName(tnAssemble)

                    //Create our validation Task (use to make sure our name is present)
                    Task taskValidate = project.task(tnValidate)

                    println(variantName)

                    taskValidate.doLast {
                        if (!PluginUtils.is2Dimension(variant)) {
                            println()
                            println "Validating App Name"
                            println()
                            println "Validated App Name: " + AppCenterValidator.validate(variant)
                            println()
                        } else {
                            println()
                            println "Validating 2 dimension App Name"
                            println()
                            println "Validated 2 dimension App Name: " + AppCenterValidator.validate2Dimension(variant)
                            println()

                        }

                    }

                    taskValidate.setGroup(GROUP_NAME)

                    //Create our deployment task

                    Task taskDeploy = project.task(tnDeploy)

                    taskDeploy.doLast {
                        singleDeploy(project, variant)
                    }

                    taskDeploy.setGroup(GROUP_NAME)

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
                                println("PARENT TASK NAME : " + task.name)
                                return task
                            }
                    }
                }

                taskParent.setGroup(GROUP_NAME)
        }
    }

    void singleDeploy(Project project, ApplicationVariant variant) {
        if (!PluginUtils.shouldCreateTask("release", variant.name)) {
            return
        }

        String apk = null
        String appName
        String appId = variant.applicationId
        String ownerName = variant.productFlavors[0].extensions.findByName("appCenter").ownerName

        variant.outputs.each {
            output ->
                apk = output.outputFile
        }


        //Pull whatever value we need
        if (AppCenterValidator.is2Dimension(variant)) {
            Map<String, String> appNames = AppCenterValidator.validate2Dimension(variant)
            appNames.each { app ->
                if (variant.name.toLowerCase().contains(app.key.toLowerCase())) {
                    jsonArray.add(createJsonObject(apk, app.value, appId, ownerName))
                }
            }
        } else {
            appName = AppCenterValidator.validate(variant)
            jsonArray.add(createJsonObject(apk, appName, appId, ownerName))
        }


        saveFile(jsonArray)
        //Try to write our file to Envman
        if (this.project.bitrise.envManEnabled) {
            try {
                project.exec {
                    workingDir BUILD_DIR
                    commandLine 'envman', 'add', '--key', 'APPCENTERBUILDSJSON', '--value', jsonArray.toString()
                    println "Wrote json to ENV VAR"
                }
            } catch (Exception ignored) {
                println "Error unable to locate envman skipping step"
            }
        }
    }

    static JsonObject createJsonObject(String apk, String appName, String appId, String ownerName) {
        JsonObject jsonObject = new JsonObject()
        println()
        println('')
        println('apk: ' + apk)
        println('appName: ' + appName)
        println('appId: ' + appId)
        println('ownerName: ' + ownerName)
        println()

        jsonObject.addProperty("build", apk)
        jsonObject.addProperty("appName", appName)
        jsonObject.addProperty("ownerName", ownerName)

        return jsonObject
    }
/**
 * Applies a branch ribbon based on the branch name
 */
    void applyBranchRibbonizer() {
        if (this.project.bitrise.branchMode) {
            String branchName = PluginUtils.getBranchName(project)

            if (branchName.contains("feat") || branchName.contains("hot")) {
                if (project.hasProperty("ribbonizer")) {
                    String ribbonColor = branchName.contains("feat") ? RC_FEATURE : RC_HOTFIX
                    String ribbonName = branchName.contains("feat") ? RN_FEATURE : RN_HOTFIX
                    project.ribbonizer {
                        builder { variant, iconFile ->
                            def filter = customColorRibbonFilter(variant, iconFile, ribbonColor)
                            filter.label = ribbonName
                            return filter
                        }
                    }
                } else {
                    println("Missing Ribbonizer, skipping ribbonization")
                }
            }
        } else {
            println("Branch mode disabled, skipping ribbonizer!")
        }
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

    static void saveFile(JsonArray array) {
        String fileName = "appcenterbuilds.json"
        def hockeyFilePath = new File(BUILD_DIR, fileName).toString()
        println "hockeyFilePath: " + hockeyFilePath
        def stringsFile = new File(hockeyFilePath)

        if (!stringsFile.exists()) stringsFile.createNewFile()

        stringsFile.text = array
    }
}
