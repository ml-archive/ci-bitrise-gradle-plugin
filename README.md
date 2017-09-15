# Bitrise CI Plugin
Android Gradle plugin for Bitrise CI


## Bitrise Config

```groovi
bitrise{
    defaultDeployMode="release|staging"
    flavorFilter = "firstSkin"
    envManEnabled = false
    branchMode = true
}
```
`defaultDeployMode` Which modes should be deployed if no mode is present in the flavor (Optional)  
`flavorFilter` Can specify which flavor should be deployed (Optional)  
`envManEnabled` Should the plugin use EnvMan by default (default = false)  
`branchMode` Should the app use Branch Mode (default = true)  

**Branch mode**  
Branch mode allows the plugin to check which branch the app is currently being built from and apply that to the version name for example if
the branch was being built on `feature/ARandomFeature` the versionName would reflect that `1.0.0-ARANDOMFEATURE`


## Android Flavor Config

```groovi
productFlavors {
    firstSkin {
        applicationId = "com.example.app.firstSkin"
        hockeyAppId = "yourKeyShouldGoHere"
        hockeyAppIdStaging = "yourKeyShouldGoHere"
        deploy = "release|staging"
    }
    secondSkin {
        applicationId = "com.example.app.secondSkin"
        hockeyAppIdStaging = "yourKeyShouldGoHere"
        deploy = "staging"
    }
    thirdSkin {
        applicationId = "com.example.app.thirdSkin"
        hockeyAppIdStaging = "yourKeyShouldGoHere"
        hockeyAppId = "yourKeyShouldGoHere"
    }
}
```
 
`hockeyAppId` Your Hockey App ID   
`hockeyAppIdStaging` Your Hockey App Staging ID    
`deploy` Specify which build should be deployed (Optional)   

## Example Output

Once the plugin runs the output should look something like

```
[
  {
    "build": "app-first.apk",
    "hockeyId": "yourKeyShouldGoHere",
    "appId": "com.example.app.firstSkin",
    "mappingFile": "null"
  },
  {
    "build": "app-second.apk",
    "hockeyId": "yourKeyShouldGoHere",
    "appId": "com.example.app.secondSkin",
    "mappingFile": "null"
  },
  {
    "build": "app-third.apk",
    "hockeyId": "yourKeyShouldGoHere",
    "appId": "com.example.app.thirdSkin",
    "mappingFile": "null"
  }
]
```
	  
