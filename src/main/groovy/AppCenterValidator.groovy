import com.android.build.gradle.api.ApplicationVariant
import com.android.builder.model.ProductFlavor
import org.gradle.api.GradleException

class AppCenterValidator {

    static String validate(ApplicationVariant variant) {
        String variableName = variant.productFlavors[0].ext.get("appCenter")
        variant.productFlavors.each { flavor ->
            flavor.getDimension()
        }
        if (variableName != null && variableName.length() != 0) {
            return variableName
        }


        def extension = variant.productFlavors[0].extensions.findByName("appCenter")
        String extensionName = extension.appName

        if (extensionName != null && extensionName.length() != 0) {
            return extensionName
        }

        if (!is2Dimension(variant)) {
            throw new GradleException("Missing AppCenter App Name Provided for flavor " + variant.productFlavors[0].name)
        }
    }

    static Map<String, String> validate2Dimension(ApplicationVariant variant) {
        def extension = variant.productFlavors[0].extensions.findByName("appCenter")

        Map<String, String> map = new HashMap<>()
        if (extension.development.length() != 0) {
            map.put("development", extension.development)
        }

        if (extension.production.length() != 0) {
            map.put("production", extension.production)
        }

        if (extension.staging.length() != 0) {
            map.put("staging", extension.staging)
        }

        String variableName = variant.productFlavors[0].ext.get("appCenter")
        String extensionName = extension.appName
        if ((variableName != null && variableName.length() != 0) || (extensionName != null && extensionName.length() != 0)) {
            throw new GradleException("appName and flavors can not be assigned at the same time appName : " + variableName + extensionName + " flavor names : " + map.toString())
        }

        if (map.isEmpty()) {
            throw new GradleException("Missing AppCenter 2 dimension app names " + map.toString())
        }

        return map
    }

    static boolean is2Dimension(ApplicationVariant variant) {
        List<String> dimensions = new ArrayList<>()
        variant.productFlavors.each { flavor ->
            dimensions.add(flavor.getDimension())
        }
        return dimensions.size() > 1
    }
}
