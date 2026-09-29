// Run from the repository root: swift scripts/prepare-branding.swift
// Mechanical cropping/resizing only; artwork comes from the approved reference.
import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let sourceURL = root.appendingPathComponent("docs/branding/ngantriin-transparent.png")
guard let source = CGImageSourceCreateWithURL(sourceURL as CFURL, nil),
      let master = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    fatalError("Cannot read branding master")
}

func save(_ image: CGImage, _ path: String) {
    let url = root.appendingPathComponent(path)
    try! FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, image, nil)
    precondition(CGImageDestinationFinalize(destination))
}

func canvas(_ image: CGImage, size: Int, contentHeight: Double, white: Bool = false, round: Bool = false) -> CGImage {
    let context = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8,
                            bytesPerRow: size * 4, space: CGColorSpaceCreateDeviceRGB(),
                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    let bounds = CGRect(x: 0, y: 0, width: size, height: size)
    if round {
        context.addEllipse(in: bounds)
        context.clip()
    } else if white {
        let inset = Double(size) * 0.02
        let radius = Double(size) * 0.20
        context.addPath(CGPath(roundedRect: bounds.insetBy(dx: inset, dy: inset),
                              cornerWidth: radius, cornerHeight: radius, transform: nil))
        context.clip()
    }
    if white { context.setFillColor(CGColor(gray: 1, alpha: 1)); context.fill(bounds) }
    let height = Double(size) * contentHeight
    let width = height * Double(image.width) / Double(image.height)
    context.interpolationQuality = .high
    context.draw(image, in: CGRect(x: (Double(size) - width) / 2,
                                   y: (Double(size) - height) / 2, width: width, height: height))
    return context.makeImage()!
}

let logo = master.cropping(to: CGRect(x: 140, y: 125, width: 974, height: 984))!
let mark = master.cropping(to: CGRect(x: 398, y: 128, width: 456, height: 670))!
save(logo, "app/src/main/res/drawable-nodpi/ngantriin_logo.png")
save(mark, "docs/branding/ngantriin-mark.png")
for (density, size) in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)] {
    let scale = Double(size) / 48
    // Adaptive foreground: 108dp canvas, artwork inside the central 66dp circle.
    save(canvas(mark, size: Int(108 * scale), contentHeight: 0.54),
         "app/src/main/res/drawable-\(density)/ic_launcher_foreground.png")
    // System splash uses the complete logo from the first frame, including its
    // wordmark and tagline. Fit the square inside Android's 192dp circular mask.
    save(canvas(logo, size: Int(288 * scale), contentHeight: 0.46),
         "app/src/main/res/drawable-\(density)/ic_splash_icon.png")
    for round in [false, true] {
        let name = round ? "ic_launcher_round" : "ic_launcher"
        save(canvas(mark, size: size, contentHeight: 0.74, white: true, round: round),
             "app/src/main/res/mipmap-\(density)/\(name).png")
    }
}
print("Prepared full logo, symbol, splash, adaptive foreground and launcher densities.")
