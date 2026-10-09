package com.hellohealth.ui.charts

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The project's single dataviz-validated categorical chart palette (blue / orange / aqua), assigned
 * in fixed order. Each hue has a light and dark step so it holds up on either surface; the set passes
 * the colorblind-safety + lightness + contrast checks the dataviz method requires, unlike the app's
 * older ad-hoc chart hues. Shared so every chart (Insights cross-dimension views, body-composition
 * trends, …) draws from the same validated colors rather than re-picking inline hex.
 *
 * Identity must never be color-alone: charts using these still carry a legend and/or direct value
 * labels. Use the [chartSeries1]/[chartSeries2]/[chartSeries3] composables so dark mode is honored.
 */
object ChartPalette {
    val series1Light = Color(0xFF2a78d6); val series1Dark = Color(0xFF3987e5) // blue
    val series2Light = Color(0xFFeb6834); val series2Dark = Color(0xFFd95926) // orange
    val series3Light = Color(0xFF1baf7a); val series3Dark = Color(0xFF199e70) // aqua
}

@Composable fun chartSeries1(): Color = if (isSystemInDarkTheme()) ChartPalette.series1Dark else ChartPalette.series1Light
@Composable fun chartSeries2(): Color = if (isSystemInDarkTheme()) ChartPalette.series2Dark else ChartPalette.series2Light
@Composable fun chartSeries3(): Color = if (isSystemInDarkTheme()) ChartPalette.series3Dark else ChartPalette.series3Light
