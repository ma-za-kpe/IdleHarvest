---
inclusion: fileMatch
fileMatchPattern: "**/*Theme*,**/*Color*,**/*Brand*,**/*theme*,**/*color*,**/ui/**,**/components/**,**/*.css"
---

# IdleHarvest Brand Identity & Design System

## Style: Bold & Symbolic
- Stylized phone + glowing harvest elements
- Tone: Modern, trustworthy, empowering, slightly vibrant (Africa-friendly energy)

## Primary Palette
| Role | Color | Hex |
|------|-------|-----|
| Primary (tech + growth) | Emerald Green | `#0A7C6B` |
| Accent (earnings) | Vibrant Gold | `#FFB300` |
| Depth (trust) | Deep Navy | `#0F1C3A` |
| Light background | Light Neutral | `#F8F9FA` |
| Dividers / secondary text | Warm Gray | `#E5E7EB` |

## Typography
- **Heading**: Inter Bold / Poppins SemiBold
- **Body**: Inter Regular
- **Logo text**: Inter ExtraBold with 1.5sp letter spacing

## Design Tokens Location
- KMP shared: `shared/src/commonMain/kotlin/com/maku/idleharvest/ui/theme/`
- Android XML: `androidApp/src/main/res/values/colors.xml`
- Web CSS: `web/brand/idleharvest-tokens.css`
- Brand object: `IdleHarvestBrand.kt`

## Key Rules
- All touch targets minimum 48dp (WCAG compliance)
- Cards use 16dp corner radius
- Buttons use 12dp corner radius, 52dp height
- Spacing follows 4dp grid
- Support both light and dark themes
- Use `IdleHarvestTheme` composable — never raw `MaterialTheme`
- Access extended colors via `IdleHarvestTheme.extendedColors`
- Health indicator: green (#10B981), yellow (#F59E0B), red (#EF4444)
- Earning source chart colors: Airtime=Primary, DePIN=Purple, Mesh=Blue, Nano=Gold
