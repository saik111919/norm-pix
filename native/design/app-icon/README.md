# App icon

The launcher icons are generated from two pictures made with Gemini:

- `emerald-default.png`: the default icon (the realistic stack of cards).
- `emerald-styles.png`: a 3 x 3 grid of the same symbol in other styles (glossy, comic, minimal, flat, glass, clay,
  pixel, paper, neon). These are the icons the user can pick in Settings > Appearance > App icon.

Each picture becomes the two layers of an Android adaptive icon. The background layer is a smooth fit of the
picture's own background. The foreground layer is the symbol, faded out at its edge and scaled to fit the 66 dp safe
zone. The themed-icon layer (`res/drawable/ic_launcher_monochrome.xml`) is drawn by hand and shared by every icon.

## Scripts

Run each script in a new PowerShell process (`powershell -File <script>`). They use Windows PowerShell 5.1 and
System.Drawing, so nothing needs to be installed.

- `specs.ps1`: where each icon sits in its picture: the tile, its frame inset, the symbol's box, and how much
  background is kept around the symbol. It also takes the generator's sparkle watermark out of both pictures.
- `preview.ps1`: writes each icon's layers and a sheet of them (round, squircle and small) to
  `%TEMP%\normpix-app-icon`. Check this before changing the app.
- `build.ps1`: writes the icons into `app/src/main/res`. The default icon gets every density
  (`drawable-*dpi/ic_launcher_foreground|background.png`). Each other icon is written once, to
  `drawable-nodpi/ic_launcher_<name>_foreground|background.png`. There are no PNG launcher icons for Android 7 and
  older, because minSdk is 26.
- `IconKit.cs`: the image code the scripts load.

## Adding a shade or a style

1. Put the new picture here, and add a spec for each icon in `specs.ps1`. Measure the tile and symbol boxes on the
   picture (open it at 2x).
2. Run `preview.ps1` and check the sheet.
3. For a new icon, also add:
   - `res/mipmap-anydpi/ic_launcher_<name>.xml`
   - an `<activity-alias>` named `.Icon<Name>` in `AndroidManifest.xml`
   - an `AppIcon` entry in `settings/AppIcons.kt`

   `AppIconsTest` checks that these match.
4. Run `build.ps1`.
