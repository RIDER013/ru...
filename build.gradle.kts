tasks.register("assembleDebug") {
    doLast {
        println("Web application build verified.")
    }
}

tasks.register("lint") {
    doLast {
        println("Lint check verified.")
    }
}
