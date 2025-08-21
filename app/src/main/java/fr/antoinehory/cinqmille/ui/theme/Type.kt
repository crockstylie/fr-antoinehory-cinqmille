// Dans app/src/main/java/fr/antoinehory/cinqmille/ui/theme/Type.kt
package fr.antoinehory.cinqmille.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Utilise une police système monospacée
val MonospaceFontFamily = FontFamily.Monospace

// Remplace la Typography existante par celle-ci
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = MonospaceFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = NeonWhite // Couleur par défaut pour le corps du texte
    ),
    titleLarge = TextStyle(
        fontFamily = MonospaceFontFamily,
        fontWeight = FontWeight.Bold, // titres en gras
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = NeonCyan // Couleur par défaut pour les titres
    ),
    labelLarge = TextStyle( // Pour les boutons
        fontFamily = MonospaceFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp, // Un peu plus grand pour les boutons
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
        color = NeonYellow // Couleur par défaut pour le texte des boutons
    ),
    // Tu peux définir d'autres styles (displayLarge, headlineSmall, etc.) si nécessaire
    // en utilisant MonospaceFontFamily et tes couleurs néon.
    bodyMedium = TextStyle( // Pour les scores, par exemple
        fontFamily = MonospaceFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = NeonWhite
    ),
    displayMedium = TextStyle( // Pour les chiffres des dés
        fontFamily = MonospaceFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp, // Plus grand pour les dés
        color = NeonYellow
    )
)
