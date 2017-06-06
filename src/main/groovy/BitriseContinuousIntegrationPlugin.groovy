import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Plugin
import org.gradle.api.Project

class BitriseContinuousIntegrationPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {

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
            flavor.ext.set("deploy", "all")
        }

        //Execute our plugin after assembleRelease

        project.afterEvaluate {
            project.tasks.findByName("assembleRelease") << {
                project.tasks.findByName("ContinuousIntegration").execute()
            }
        }

        //Create our task
        project.task("ContinuousIntegration") {
            def projectPath = project.buildDir.toString();

            def hockeyFilePath = new File(projectPath, "hockeybuilds.json").toString()

            def stringsFile = new File(hockeyFilePath)

            def jsonArray = new JsonArray()

            def defaultDeployMode = project.bitrise.defaultDeployMode

            project.android.applicationVariants.all { variant ->
                def apk = null
                def hockeyId = null
                def appId = variant.applicationId
                def mappingFile = variant.mappingFile
                def deployMode = (variant.productFlavors[0].ext.has("deploy")) ? variant.productFlavors[0].ext.get("deploy") : defaultDeployMode
                def deployable = false

                if (deployMode.contains("|")) {
                    def deployModes = deployMode.split("|")
                    for (def mode : deployModes) {
                        if ((variant.name).toString().toLowerCase().contains(mode.toLowerCase())) {
                            deployable = true
                        }
                    }
                } else if ((variant.name).toString().toLowerCase().contains(deployMode.toLowerCase())) {
                    deployable = true
                }

                if (!deployable) {
                    println "Ignoring: " + variant.name
                    return
                }

                if ((variant.name).contains("Staging") && (variant.productFlavors[0].ext.has("hockeyAppIdStaging"))) {
                    hockeyId = variant.productFlavors[0].ext.get("hockeyAppIdStaging")

                    // Are we supposed to
                } else if ((variant.name).contains("Release") && (variant.productFlavors[0].ext.has("hockeyAppId"))) {
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
                jsonObject.addProperty("mappingFile", (String) mappingFile)
                jsonObject.addProperty("deploy", deployMode)

                jsonArray.add(jsonObject)

            }

            doLast {
                def json = jsonArray.toString();

                println "HockeySDK JSON output file:"

                println json

                stringsFile.text = json
            }
        }
    }
}
