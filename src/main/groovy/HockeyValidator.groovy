import com.android.build.gradle.api.ApplicationVariant
import org.gradle.api.GradleException

class HockeyValidator {
    public static final String HOCKEY_ID_TYPE_DEVELOPMENT = "hockeyAppIdDevelopment"
    public static final String HOCKEY_ID_TYPE_STAGING = "hockeyAppIdStaging"
    public static final String HOCKEY_ID_TYPE_RELEASE = "hockeyAppId"
    public static final String HOCKEY_TYPE_RELEASE = "release"
    public static final String HOCKEY_TYPE_STAGING = "staging"
    public static final String HOCKEY_TYPE_DEBUG = "debug"
    private static final int HOCKEY_ID_LENGTH = 32 //Length of the Hockey ID

    static String validate(ApplicationVariant variant) {
        String hockeyId = null

        variant.getProductFlavors().each { flavor ->
            flavor

        }

        boolean isDebug = PluginUtils.containsIgnoreCase(variant.name, HOCKEY_TYPE_DEBUG)
        boolean isStaging = PluginUtils.containsIgnoreCase(variant.name, HOCKEY_TYPE_STAGING)
        boolean isRelease = PluginUtils.containsIgnoreCase(variant.name, HOCKEY_TYPE_RELEASE)

        if (isDebug) {
            hockeyId = variant.productFlavors[0].ext.get(HOCKEY_ID_TYPE_DEVELOPMENT) ?: variant.productFlavors[0].ext.get(HOCKEY_ID_TYPE_STAGING)
            checkHockeyID(hockeyId, HOCKEY_ID_TYPE_DEVELOPMENT)
        }

        if (isStaging) {
            hockeyId = variant.productFlavors[0].ext.get(HOCKEY_ID_TYPE_STAGING) ?: variant.productFlavors[0].ext.get(HOCKEY_ID_TYPE_RELEASE)
            checkHockeyID(hockeyId, HOCKEY_ID_TYPE_STAGING)
        }

        if (isRelease) {
            hockeyId = variant.productFlavors[0].ext.get(HOCKEY_ID_TYPE_RELEASE)
            checkHockeyID(hockeyId, HOCKEY_ID_TYPE_RELEASE)
        }

        if (hockeyId == null) {
            throw new GradleException("Unsupported Deployment Mode")
        }

        return hockeyId
    }

    private static void checkHockeyID(String hockeyID, String HockeyIDType) {
        if (hockeyID == null || hockeyID.length() == 0) {
            throw new GradleException("Missing Hockey ID Provided for field " + HockeyIDType)
        } else if (hockeyID.length() != HOCKEY_ID_LENGTH) {
            throw new GradleException("Invalid Hockey ID Provided for field " + HockeyIDType)
        }
    }
}
