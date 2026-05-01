allprojects {
    group = "io.github.grpckotlin"
    version = "0.1.0-SNAPSHOT"
}

tasks.named<Wrapper>("wrapper") {
    distributionType = Wrapper.DistributionType.BIN
    validateDistributionUrl = true
}
