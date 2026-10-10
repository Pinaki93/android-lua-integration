plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val luaCompiler by configurations.creating
val luaSources = rootProject.layout.projectDirectory.dir("lua")
val compiledLua = layout.buildDirectory.dir("generated/luaAssets")
val cleanCompiledLua by tasks.registering(Delete::class) {
    delete(compiledLua)
}
val luaCompileTasks = fileTree(luaSources) { include("**/*.lua") }.files.map { source ->
    val relativePath = source.relativeTo(luaSources.asFile).path
    tasks.register<JavaExec>("compileLua${relativePath.replace(Regex("[^A-Za-z0-9]"), "_")}") {
        dependsOn(cleanCompiledLua)
        mustRunAfter(cleanCompiledLua)
        classpath = luaCompiler
        mainClass.set("luac")
        val output = compiledLua.map { it.file(relativePath.removeSuffix(".lua") + ".luac") }
        inputs.file(source)
        outputs.file(output)
        doFirst { output.get().asFile.parentFile.mkdirs() }
        args("-s", "-o", output.get().asFile.absolutePath, source.absolutePath)
    }
}
val compileLua by tasks.registering {
    dependsOn(luaCompileTasks)
}

android {
    namespace = "com.example.luaplayground"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.luaplayground"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets.named("main") {
        assets.directories.add(compiledLua.get().asFile.absolutePath)
    }
}

tasks.named("preBuild").configure { dependsOn(compileLua) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    implementation(project(":lua-compose"))
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("org.luaj:luaj-jse:3.0.1")
    implementation("io.ktor:ktor-client-core:3.3.3")
    implementation("io.ktor:ktor-client-android:3.3.3")
    testImplementation("io.ktor:ktor-client-mock:3.3.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    luaCompiler("org.luaj:luaj-jse:3.0.1")
}
