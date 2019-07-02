import com.android.build.gradle.api.ApplicationVariant
import org.gradle.api.Project

class PluginUtils {

    static List<String> getTaskNames(Project project) {
        List<String> taskNames = new ArrayList<>()

        for (ApplicationVariant variant : project.android.applicationVariants) {
            String variantName = capFirstLetter(variant.name)


            String taskName = String.format(BitriseContinuousIntegrationPlugin.FORMAT_TASK_NAME, variantName)

            if (shouldCreateTask("release", variantName)) {
                taskNames.add(taskName)
            }
        }

        return taskNames
    }

    static String getBranchName(Project project) {
        def hashStdOut = new ByteArrayOutputStream()

        try {
            project.exec {
                commandLine "git", "rev-parse", "--abbrev-ref", "HEAD"
                standardOutput = hashStdOut
            }
        } catch (Exception ignored) {
            //Do nothing
        }

        return hashStdOut.toString().trim()
    }

    static List<String> getFilteredTaskNames(Project project) {
        List<String> taskNames = new ArrayList<>()

        List<String> filterNames = getFilterNames(project)

        for (ApplicationVariant variant : project.android.applicationVariants) {
            String variantName = capFirstLetter(variant.name)


            //If our filter list size is 0 then we should always return true because that means no filters :)

            boolean isInFilter = stringContainsList(variant.name, filterNames) || filterNames.size() == 0

            String taskName = String.format(BitriseContinuousIntegrationPlugin.FORMAT_TASK_NAME, variantName)

            if (isInFilter && shouldCreateTask("release", variantName)) {
                taskNames.add(taskName)
            }
        }

        return taskNames
    }

    static List<String> getFilterNames(Project project) {
        String flavorNames = project.bitrise.flavorFilter

        String[] filters = flavorNames.tokenize("|")

        return Arrays.asList(filters)
    }

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

    static boolean stringContainsList(String haystack, List<String> needle) {
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

    static boolean arrayContainsExactString(List<String> haystack, String needle) {
        needle = needle.toLowerCase()
        for (String hay : haystack) {
            if (hay.equalsIgnoreCase(needle)) {
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

    static boolean is2Dimension(ApplicationVariant variant) {
        def extension = variant.productFlavors[0].extensions.findByName("appCenter")
        if (extension.development.length() != 0) {
            return true
        } else if (extension.staging.length() != 0) {
            return true
        } else if (extension.production.length() != 0) {
            return true
        }
        return false
    }

    static boolean shouldCreateTask(String mode, String variantName) {
        return variantName.toLowerCase().contains(mode.toLowerCase())
    }
}
