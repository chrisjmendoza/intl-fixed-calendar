# Brand assets

The Yearal launcher icon: the "perfect month" — 28 days as a 4 × 7 grid of dots with Year Day beneath,
outside the week. Designed 2026-09-18 (ROADMAP.md open decision #10).

| File | Use |
|---|---|
| [yearal-icon.svg](yearal-icon.svg) | Source, 512 × 512, full-bleed. Teal `#123F3D`, cream `#F4ECDA`, accent `#F28C28`. |
| [yearal-icon-mono.svg](yearal-icon-mono.svg) | Monochrome variant (ink `#244338` on `#DCE7DF`) — the themed-icon layer. |
| [yearal-icon-512.png](yearal-icon-512.png) | Square 512 px render for the Play listing (Play applies its own mask). |
| [yearal-icon-mono-512.png](yearal-icon-mono-512.png) | Square render of the monochrome variant. |
| `*-round-preview.png` | The designer's circular previews; not used by the build. |
| `candidates/almanac-13-*.png` | The runner-up concept ("Almanac 13": a calendar page with a serif 13). Not chosen: the numeral is a web font that would have to be converted to vector outlines, and a page-with-a-number is the generic calendar-app shape. Kept for reference. |

The app does **not** ship these PNGs. The adaptive icon in `app/src/main/res` is built from vector layers
(`drawable/ic_launcher_foreground.xml`, `drawable/ic_launcher_monochrome.xml`, colour
`ic_launcher_background`) that map the 512 canvas 1:1 onto the 108 dp adaptive-icon canvas, so every
density and launcher shape is rendered by the system. Edit the SVG, then regenerate the two `pathData`
strings the same way (the designer's 512 canvas scaled by 1.12 about the glyph centre so it fills more of the launcher
mask while every corner dot stays inside the 66 dp safe zone: the dot grid is `cx ∈ {141.8 … 370.2 step 38.1}`,
`cy ∈ {168.7 … 282.9 step 38.1}`, `r = 12.3`; the pill is `217.9,330.0 76.2 × 24.6 rx 12.3`). The brand colours are resources in
`app/src/main/res/values/ic_launcher_background.xml`.
