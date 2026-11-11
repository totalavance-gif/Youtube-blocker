# YouTubeBlocker v1.1

Cliente YouTube en WebView con auto-salto de anuncios (16x + clic),
velocidades 1–2x, segundo plano (servicio + notificación + wake lock)
y estadísticas locales. Java 7, minSdk 19, sin AndroidX.

Fuente reconstruido desde el APK v1.0 + mejoras al salto de anuncios.

## Novedades v1.1 (salto de anuncios)

- `MutationObserver` con freno de 150 ms: salta casi al instante de
  aparecer el botón; el intervalo de 1200 ms queda como respaldo.
- Cierra banners overlay (`overlay-close`) en pasada aparte, sin tocar
  la velocidad del contenido.
- Silencia durante anuncios y restaura el muteo anterior al terminar.
- Selector extra de botón saltar (`.ytp-ad-skip-button-slot`).
- `apply()` en vez de `commit()` en contadores.

## Compilar

- AIDE: abre la carpeta del proyecto y Run (estructura clásica intacta:
  `src/`, `res/`, `assets/`, `AndroidManifest.xml`).
- CI: cada push a `main` compila en Actions (pestaña **Actions** →
  artifact **app-debug**). Gradle reutiliza los mismos fuentes vía
  `sourceSets`; el `package` del manifest se filtra a una copia en
  `build/` (AGP 8 lo exige en `namespace`, AIDE lo necesita en el XML).
- `gradle-stubs/` solo existe para CI: AIDE provee su propia clase ADRT.

## Probar

Video con anuncio saltable (salta solo), bumper no saltable
(pasa volando y mudo), banner overlay (se cierra solo), video normal
(velocidad intacta) y segundo plano con pantalla apagada.
