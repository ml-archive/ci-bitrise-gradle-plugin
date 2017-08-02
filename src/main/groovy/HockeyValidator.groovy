import com.android.build.gradle.api.ApplicationVariant
import org.gradle.api.GradleException

class HockeyValidator {

    private static final int HOCKEY_ID_LENGTH = 32 //Length of the Hockey ID

    static String validate(ApplicationVariant variant) {
        String hockeyId = null;

        if (variant.productFlavors.size() > 1) {
            //apps with dimensions like riide
            if (variant.productFlavors[0].ext.hockeyAppId instanceof Map && variant.productFlavors[0].ext.hockeyAppId[variant.productFlavors[1].name] != null) {
                //When environment keys specified and the info is in the first dimension
                hockeyId = variant.productFlavors[0].ext.hockeyAppId[variant.productFlavors[1].name]
                checkHockeyID(hockeyId, variant.productFlavors[1].name)
            } else if (variant.productFlavors[1].ext.hockeyAppId instanceof Map && variant.productFlavors[1].ext.hockeyAppId[variant.productFlavors[0].name] != null) {
                //When environment keys specified and the info is in the second dimension
                hockeyId = variant.productFlavors[1].ext.hockeyAppId[variant.productFlavors[0].name]
                checkHockeyID(hockeyId, variant.productFlavors[0].name)
            } else if (variant.productFlavors[0].ext.hockeyAppId instanceof String) {
                //When only an unique key has been specified in the first dimension
                hockeyId = variant.productFlavors[0].ext.hockeyAppId
                checkHockeyID(hockeyId, variant.productFlavors[0].name)
            } else if (variant.productFlavors[1].ext.hockeyAppId instanceof String) {
                //When only an unique key has been specified in the second dimension
                hockeyId = variant.productFlavors[1].ext.hockeyAppId
                checkHockeyID(hockeyId, variant.productFlavors[1].name)
            }
        } else if (variant.productFlavors[0].ext.hockeyAppId instanceof String) {
            //normal apps
            hockeyId = variant.productFlavors[0].ext.hockeyAppId
            checkHockeyID(hockeyId, variant.productFlavors[0].name)
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
