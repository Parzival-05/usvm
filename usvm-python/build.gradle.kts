plugins {
    id("usvm.kotlin-conventions")
}


dependencies {
    implementation(project(":usvm-core"))

    implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-cvc5:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-bitwuzla:${Versions.ksmt}")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")
}

tasks.assemble {
    dependsOn(":usvm-python:cpythonadapter:linkDebug")
}

val cpythonBuildPath = "${childProjects["cpythonadapter"]!!.buildDir}/cpython_build"
val cpythonAdapterBuildPath = "${childProjects["cpythonadapter"]!!.buildDir}/lib/main/debug"

tasks.register<JavaExec>("runTestKt") {
    group = "run"
    dependsOn(tasks.assemble)
    environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
    environment("LD_PRELOAD" to "$cpythonBuildPath/lib/libpython3.so")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("TestKt")
}

tasks.test {
    dependsOn(":usvm-python:cpythonadapter:linkDebug")
    environment("LD_LIBRARY_PATH" to "$cpythonBuildPath/lib:$cpythonAdapterBuildPath")
    environment("LD_PRELOAD" to "$cpythonBuildPath/lib/libpython3.so")
}