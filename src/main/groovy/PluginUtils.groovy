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

    static String searchArray(String[] haystack, String needle) {
        needle = needle.toLowerCase()
        for (String string : haystack) {
            string = string.toLowerCase()
            if (needle.contains(string)) {
                return string
            }
        }

        return ""
    }

    static boolean stringContainsArray(String haystack, String[] needle) {
        haystack = haystack.toLowerCase()
        for (String string : needle) {
            string = string.toLowerCase()
            if (haystack.contains(string)) {
                return true
            }
        }

        return false
    }

    static boolean containsIgnoreCase(String haystack, String key) {
        haystack = haystack.toLowerCase()

        key = key.toLowerCase()

        return haystack.contains(key)
    }

    static boolean arrayContainsString(List<String> haystack, String needle) {
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
        String defaultDeployMode = project.bitrise.defaultDeployMode

        String deployMode = variant.productFlavors[0].ext.get("deploy")

        deployMode = deployMode.length() == 0 ? defaultDeployMode : deployMode

        return deployMode.tokenize("|")
    }

    static String capFirstLetter(final String line) {
        return Character.toUpperCase(line.charAt(0)).toString() + line.substring(1)
    }
}
