package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// BRAND PALETTE (LIGHT MODE)
// ==========================================
val SoftCloudWhite = Color(0xFFEFF0F4)
val PeriwinkleBlue = Color(0xFF6192FC)
val DeepRoyalNavy = Color(0xFF11358B)
val ElectricLime = Color(0xFFC7EF66)

// Neutral palette (Light)
val PureWhite = Color(0xFFFFFFFF)
val PrimaryText = Color(0xFF15213D)
val SecondaryText = Color(0xFF64708A)
val CardBorder = Color(0xFFE0E5EF)
val MutedSurface = Color(0xFFE8ECF5)

// ==========================================
// BRAND PALETTE (DARK MODE - EXACT SPECIFICATION)
// ==========================================
val DarkBackground = Color(0xFF101522)          // Main background
val DarkSecondaryBackground = Color(0xFF171E2E) // Secondary background
val DarkCardSurface = Color(0xFF1D273A)         // Cards / surfaces
val DarkPrimaryText = Color(0xFFF4F6FC)         // Primary text
val DarkSecondaryText = Color(0xFFB9C3D6)       // Secondary text
val DarkPrimaryBlue = Color(0xFF82A9FF)         // Primary blue
val DarkSecondaryBlue = Color(0xFFA9C3FF)       // Secondary blue
val DarkLimeAccent = Color(0xFFC7EF66)          // Lime accent
val DarkBorders = Color(0xFF344158)             // Borders
val DarkDisabledText = Color(0xFF8B96AA)        // Disabled text

// ==========================================
// ACCESSIBILITY & CONTRAST RULES (WCAG 4.5:1 / 3:1)
// ==========================================
// Rule 1: NEVER use white text on Electric Lime! Always Dark Royal Navy.
val OnLimeText = Color(0xFF11358B)
// Rule 2: Dark Navy on Light Blue / Cloud White backgrounds.
val OnLightBlueText = Color(0xFF11358B)
// Rule 3: Pure White text on Deep Royal Navy backgrounds.
val OnDarkBlueText = Color(0xFFFFFFFF)

// Semantic status colours
val StatusPending = Color(0xFFF6C85F)
val StatusAlert = Color(0xFFD94A4A)
val StatusAlertDark = Color(0xFFFF6B6B)
val StatusSuccess = Color(0xFF2E7D32)
val StatusSuccessDark = Color(0xFF4ADE80)
val StatusDarkElectricLime = Color(0xFF466600) // High contrast version of lime for standalone text/icons on white
