import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.ApplicationVariant
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

class BitriseContinuousIntegrationPlugin implements Plugin<Project> {
    private String taskNameFormat = "ci%s"
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
            flavor.ext.set("hockeyAppId", "")
            flavor.ext.set("hockeyAppIdStaging", "")
            flavor.ext.set("deploy", "")
        }


        project.task("generateCITasks") {
            setGroup("ci")

            generateFlavorTasks(project)


        }


        project.task("ciAll") {
            setGroup("ci")

            doLast {
                List<String> taskNames = getTaskNames()

                for (String taskName : taskNames) {
                    project.tasks.findByName(taskName).execute()
                }
            }

        }
    }


    List<String> getTaskNames() {
        List<String> taskNames = new ArrayList<>();

        for (ApplicationVariant variant : project.android.applicationVariants) {
            String variantName = PluginUtils.capFirstLetter(variant.name)

            String[] deploymentModes = PluginUtils.getDeploymentModes(project, variant)

            boolean shouldCreateTask = PluginUtils.arrayContainsString(deploymentModes, variant.name)

            String taskName = String.format(taskNameFormat, variantName)

            if (shouldCreateTask) {
                taskNames.add(taskName)
            }
        }

        return taskNames
    }

    void generateFlavorTasks(Project project) {
        println "Generating product flavors"

        //Generate the children tasks

        project.android.applicationVariants.all { variant ->

            assert variant instanceof ApplicationVariant

            String variantName = PluginUtils.capFirstLetter(variant.name);

            String assembleTaskName = String.format("assemble%s", variantName)

            String[] deploymentModes = PluginUtils.getDeploymentModes(project, variant)

            boolean shouldCreateTask = PluginUtils.arrayContainsString(deploymentModes, variant.name)

            if (shouldCreateTask) {
                String taskName = String.format(taskNameFormat, variantName)

                println taskName

                Task taskAssemble = project.tasks.findByName(assembleTaskName)

                project.task(taskName) {
                    setGroup("ci")

                    doLast {
                        JsonObject jsonObject = singleDeploy(project, variant)

                        //saveFile(variantName, jsonObject) // Commented out for now
                    }
                } dependsOn taskAssemble
            }
        }

        //Generate the parent tasks

        project.android.productFlavors.all { flavor ->
            String flavorName = PluginUtils.capFirstLetter(flavor.name)

            String taskName = String.format(taskNameFormat, flavorName)

            project.task(taskName) {
                setGroup("ci")

                doLast {
                    List<String> taskNames = getTaskNames()

                    for (String name : taskNames) {
                        if (name.contains(flavorName)) {
                            project.tasks.findByName(name).execute()
                        }
                    }
                }
            }

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

        def apk = null
        def hockeyId = null
        def appId = variant.applicationId
        def mappingFile = variant.mappingFile

        String[] deploymentModes = PluginUtils.getDeploymentModes(project, variant)

        boolean shouldDeploy = PluginUtils.arrayContainsString(deploymentModes, variant.name)

        if (!shouldDeploy) {
            return
        }

        boolean isStaging = PluginUtils.arrayContainsString(deploymentModes, "hockeyAppIdStaging")

        boolean isRelease = PluginUtils.arrayContainsString(deploymentModes, "hockeyAppId")

        if (isStaging && (variant.productFlavors[0].ext.has("hockeyAppIdStaging"))) {
            hockeyId = variant.productFlavors[0].ext.get("hockeyAppIdStaging")
        }

        if (isRelease && (variant.productFlavors[0].ext.has("hockeyAppId"))) {
            hockeyId = variant.productFlavors[0].ext.get("hockeyAppId")
        }

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

        JsonObject jsonObject = new JsonObject()

        jsonObject.addProperty("build", (String) apk)
        jsonObject.addProperty("hockeyId", (String) hockeyId)
        jsonObject.addProperty("appId", (String) appId)
        jsonObject.addProperty("mappingFile", (String) mappingFile.toString())
        jsonObject.addProperty("deploy", Arrays.toString(deploymentModes))

        return jsonObject
    }
}
