# Bitrise CI Plugin
Android Gradle plugin for Bitrise CI


## Bitrise Config

```groovi
bitrise{
    defaultDeployMode="release|staging"
    flavorFilter = "firstSkin"
}
```
`defaultDeployMode` Which modes should be deployed if no mode is present in the flavor (Optional)  
`flavorFilter` Can specifiy which flavor should be deployed (Optional)

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
    secondSkin {
        applicationId = "com.example.app.secondSkin"
        hockeyAppIdStaging = "yourKeyShouldGoHere"
        hockeyAppId = "yourKeyShouldGoHere"
    }
}
```
 
`hockeyAppId` Your Hockey App ID (Optional)   
`hockeyAppIdStaging` Your Hockey App Staging ID   
`deploy` Specify which build should be deployed (Optional)   





