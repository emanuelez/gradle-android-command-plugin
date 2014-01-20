package com.novoda.gradle.command

import org.gradle.api.Project

public class AndroidCommandPluginExtension {

    def androidHome
    def adb
    def aapt
    def deviceId

    private final Project project

    AndroidCommandPluginExtension(Project project) {
        this.project = project
        androidHome = readSdkDirFromLocalProperties() ?: System.env.ANDROID_HOME
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    def tasks(String name, Closure configuration) {
        tasks(name).all(configuration)
    }

    def tasks(String name) {
        tasks(name, Adb)
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    def tasks(String name, Class<? extends Adb> type, Closure configuration) {
        tasks(name, type).all(configuration)
    }

    def tasks(String name, Class<? extends Adb> type) {
        VariantConfigurator variantConfigurator = new VariantConfigurator(project, name, type)
        project.android.applicationVariants.all {
            variantConfigurator.configure(it)
        }
        project.tasks.matching {
            it.name.startsWith name
        }
    }

    def getAdb() {
        adb ?: "$androidHome/platform-tools/adb"
    }

    def getAapt() {
        aapt ?: "$androidHome/build-tools/$project.android.buildToolsRevision/aapt"
    }

    // prefer system property over direct setting to enable commandline arguments
    def getDeviceId() {
        System.properties['deviceId'] ?: deviceId ?: defaultDeviceId()
    }

    def attachedDevices() {
        def devices = []
        [getAdb(), 'devices'].execute().text.eachLine { line ->
            def matcher = line =~ /^(.*)\tdevice/
            if (matcher) {
                devices << matcher[0][1]
            }
        }
        devices
    }

    def attachedDevicesWithBrand(String brand) {
        attachedDevices().findResults { deviceId ->
            def product = "$adb -s $deviceId shell getprop ro.product.brand".execute()
            String brandName = product.text.trim()
            brandName == brand ? deviceId : null
        }
    }

    private def defaultDeviceId() {
        def devices = attachedDevices()
        if (devices.isEmpty()) {
            throw new IllegalStateException('No attached devices found')
        }
        devices[0]
    }

    private def readSdkDirFromLocalProperties() {
        try {
            Properties properties = new Properties()
            properties.load(project.rootProject.file('local.properties').newDataInputStream())
            properties.getProperty('sdk.dir')
        }
        catch (Exception e) {
            null
        }
    }

}
