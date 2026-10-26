// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "NavigationCapacitorRoutingTests",
    platforms: [.iOS(.v14)],
    dependencies: [.package(name: "NavigationCapacitorPlugin", path: "../../plugin")],
    targets: [
        .testTarget(
            name: "NavigationCapacitorRoutingTests",
            dependencies: [.product(name: "NextbillionNavigationCapacitor", package: "NavigationCapacitorPlugin")],
            path: "Sources"
        )
    ]
)
