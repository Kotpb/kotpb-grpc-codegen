allprojects {
    group = "io.github.kotpb"
    version = "0.1.0-SNAPSHOT"
}

tasks.named<Wrapper>("wrapper") {
    distributionType = Wrapper.DistributionType.BIN
    validateDistributionUrl = true
}
