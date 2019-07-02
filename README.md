# Bitrise CI Plugin
Android Gradle plugin for Bitrise CI


##Apcenter Config
```groovy
class AppCenterExtension {
    String appName = ""
    String ownerName = BitriseContinuousIntegrationPlugin.DEFAULT_OWNER_NAME
    String production = ""
    String staging = ""
    String development = ""
}
```
```text
Identify the {owner_name} and {app_name} for the app that you wish to upload to.
These will be used in the URL for the API calls. For an app owned by a user, the URL in App Center might look like: 
https://appcenter.ms/users/JoshuaWeber/apps/APIExample. 
Here, the {owner_name} is JoshuaWeber and the {app_name} is ApiExample. 
For an app owned by an org, the URL might be https://appcenter.ms/orgs/Microsoft/apps/APIExample and the {owner_name} would be Microsoft.
```
If you need to use two dimension in your project you can only use these environments in AppCenter config `production` `staging` `development` 


## Bitrise Config

```groovy
bitrise{
    defaultDeployMode="release|staging"
    flavorFilter = "firstSkin"
    envManEnabled = false
    branchMode = true
}
```  
`flavorFilter` Can specify which flavor should be deployed (Optional)  
`envManEnabled` Should the plugin use EnvMan by default (default = false)  
`branchMode` Should the app use Branch Mode (default = true)  

**Branch mode**  
Branch mode allows the plugin to check which branch the app is currently being built from and apply that to the version name for example if
the branch was being built on `feature/ARandomFeature` the versionName would reflect that `1.0.0-ARANDOMFEATURE`


## Android Flavor Config

```groovy
flavorDimensions "env"
    productFlavors {
        staging {
            dimension "env"
            applicationIdSuffix ".staging"
            appCenter = "appcenter-staging"
        }

        production {
            dimension "env"
            applicationIdSuffix ".production"
            appCenter = "appcenter-production"
        }
    }
```

```groovy
flavorDimensions "env"
    productFlavors {
        staging {
            dimension "env"
            applicationIdSuffix ".staging"
            appCenter {
                appName "appcenter-staging"
                ownerName "random-owner"
            }
        }

        production {
            dimension "env"
            applicationIdSuffix ".production"
            appCenter {
                appName "appcenter-production"
                ownerName "random-owner"
            }
        }
    }
```
 

## Example Output

Once the plugin runs the output should look something like

```json
[
  {
    "build": "/Users/dtunctuncer/projects/work/AppCenterTest/app/build/outputs/apk/production/release/app-production-release-unsigned.apk",
    "appName": "appcenter-production",
    "ownerName": "Casper-Rasmussen-Organization"
  },
  {
    "build": "/Users/dtunctuncer/projects/work/AppCenterTest/app/build/outputs/apk/staging/release/app-staging-release-unsigned.apk",
    "appName": "appcenter-staging",
    "ownerName": "Casper-Rasmussen-Organization"
  }
]
```


## 2 Dimension Android Flavor Config

```groovy
flavorDimensions "app", "env"
    productFlavors {

        staging {
            dimension "env"
            applicationIdSuffix ".staging"
        }

        production {
            dimension "env"
            applicationIdSuffix ".production"
        }

        facebook {
            dimension "app"
            appCenter {
                production "facebook-production"
                staging "facebook-staging"
            }
        }
    }
}
```
 

## Example Output

Once the plugin runs the output should look something like

```json
[
  {
    "build": "/Users/dtunctuncer/projects/work/AppCenterTest/app/build/outputs/apk/facebookProduction/release/app-facebook-production-release-unsigned.apk",
    "appName": "facebook-production",
    "ownerName": "Casper-Rasmussen-Organization"
  },
  {
    "build": "/Users/dtunctuncer/projects/work/AppCenterTest/app/build/outputs/apk/facebookStaging/release/app-facebook-staging-release-unsigned.apk",
    "appName": "facebook-staging",
    "ownerName": "Casper-Rasmussen-Organization"
  }
]
```
	  
