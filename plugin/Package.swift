// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "NextbillionNavigationCapacitor",
    platforms: [.iOS(.v14)],
    products: [
        .library(name: "NextbillionNavigationCapacitor", targets: ["NBNavigationCapacitor"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.6.9"),
        .package(url: "https://github.com/nextbillion-ai/navigation-distribution", exact: "4.1.1")
    ],
    targets: [
        .target(
            name: "NBNavigationCapacitor",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm"),
                .product(name: "NbmapNavigation", package: "navigation-distribution")
            ],
            path: "ios/Sources/NBNavigationCapacitor"
        )
    ]
)
