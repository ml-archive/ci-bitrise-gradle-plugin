import com.android.build.gradle.api.ApplicationVariant
import org.gradle.api.Project

class PluginUtils {
    static boolean arrayContainsString(String[] haystack, String needle) {
        needle = needle.toLowerCase()
        for (String string : haystack) {
            string = string.toLowerCase()
            if (needle.contains(string)) {
                return true
            }
        }

        return false
    }

    static String[] getDeploymentModes(Project project, ApplicationVariant variant) {
        def defaultDeployMode = project.bitrise.defaultDeployMode

        def deployMode = (variant.productFlavors[0].ext.has("deploy")) ? variant.productFlavors[0].ext.get("deploy") : defaultDeployMode

        return deployMode.tokenize("|")
    }

    static String capFirstLetter(final String line) {
        return Character.toUpperCase(line.charAt(0)).toString() + line.substring(1)
    }
}
