$sdkPath = "$env:LOCALAPPDATA\Android\Sdk"
$cmdlineToolsUrl = "https://dl.google.com/android/repository/commandlinetools-win-10406996_latest.zip"

Write-Host "Checking for Android SDK..."

if (!(Test-Path $sdkPath)) {
    Write-Host "SDK not found at $sdkPath. Creating directory..."
    New-Item -ItemType Directory -Force -Path $sdkPath | Out-Null
    
    Write-Host "Downloading Command Line Tools..."
    Invoke-WebRequest -Uri $cmdlineToolsUrl -OutFile "$sdkPath\cmdline-tools.zip"
    
    Write-Host "Extracting Command Line Tools..."
    Expand-Archive -Path "$sdkPath\cmdline-tools.zip" -DestinationPath "$sdkPath\cmdline-tools_temp" -Force
    
    # Correct structure: cmdline-tools/latest/bin
    $latestPath = "$sdkPath\cmdline-tools\latest"
    New-Item -ItemType Directory -Force -Path $latestPath | Out-Null
    
    # Move extracted contents to 'latest'
    # The zip usually contains a 'cmdline-tools' folder at root
    Move-Item -Path "$sdkPath\cmdline-tools_temp\cmdline-tools\*" -Destination $latestPath -Force
    Remove-Item -Path "$sdkPath\cmdline-tools_temp" -Recurse -Force
    Remove-Item -Path "$sdkPath\cmdline-tools.zip" -Force
    
    Write-Host "Command Line Tools installed."
    
    # Set environment for this session
    $env:ANDROID_HOME = $sdkPath
    $env:PATH += ";$latestPath\bin"
    
    Write-Host "Installing Platform Tools and SDK Platform 34..."
    # Auto-accept licenses
    & "$latestPath\bin\sdkmanager.bat" --licenses --sdk_root="$sdkPath" | Out-Null
    & "$latestPath\bin\sdkmanager.bat" "platform-tools" "platforms;android-34" "build-tools;34.0.0" --sdk_root="$sdkPath"
    
    Write-Host "SDK Setup Complete!"

}
else {
    Write-Host "SDK found at $sdkPath"
}

# Gradle Setup
$gradleVersion = "9.1.0"
$gradleUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
$gradleBaseDir = "$env:LOCALAPPDATA\Android\Gradle"
if (Test-Path "$gradleBaseDir\gradle-9.1.0") {
    $gradleHome = "$gradleBaseDir\gradle-9.1.0"
}
else {
    $gradleHome = "$gradleBaseDir\gradle-$gradleVersion"
}

if (!(Test-Path "$gradleHome\bin\gradle.bat")) {
    Write-Host "Gradle $gradleVersion not found. Installing..."
    New-Item -ItemType Directory -Force -Path $gradleBaseDir | Out-Null
    
    $zipPath = "$gradleBaseDir\gradle.zip"
    Write-Host "Downloading Gradle..."
    Invoke-WebRequest -Uri $gradleUrl -OutFile $zipPath
    
    Write-Host "Extracting Gradle..."
    Expand-Archive -Path $zipPath -DestinationPath $gradleBaseDir -Force
    Remove-Item -Path $zipPath -Force
    
    Write-Host "Gradle installed to $gradleHome"
}
else {
    Write-Host "Gradle found at $gradleHome"
}

# Generate batch environment setup script
$envBatchPath = "$PSScriptRoot\env_setup.bat"
$content = "@echo off`n"
$content += "set ANDROID_HOME=$sdkPath`n"
$content += "set GRADLE_HOME=$gradleHome`n"
$content += "set PATH=%GRADLE_HOME%\bin;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\cmdline-tools\latest\bin;%PATH%`n"
Set-Content -Path $envBatchPath -Value $content

Write-Host "Environment setup script generated at $envBatchPath"
